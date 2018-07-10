/*
 * SonarQube Java
 * Copyright (C) 2012-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.java.cfg.CFG;
import org.sonar.java.cfg.VariableReadExtractor;
import org.sonar.java.model.LiteralUtils;
import org.sonar.java.resolve.JavaSymbol;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.ArrayAccessExpressionTree;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.ucfg.Expression;
import org.sonar.ucfg.Label;
import org.sonar.ucfg.LocationInFile;
import org.sonar.ucfg.UCFG;
import org.sonar.ucfg.UCFGBuilder;
import org.sonar.ucfg.UCFGBuilder.BlockBuilder;
import org.sonar.ucfg.UCFGtoProtobuf;

import static org.sonar.plugins.java.api.tree.Tree.Kind.ARRAY_ACCESS_EXPRESSION;
import static org.sonar.plugins.java.api.tree.Tree.Kind.ASSIGNMENT;
import static org.sonar.plugins.java.api.tree.Tree.Kind.CONSTRUCTOR;
import static org.sonar.plugins.java.api.tree.Tree.Kind.IDENTIFIER;
import static org.sonar.plugins.java.api.tree.Tree.Kind.MEMBER_SELECT;
import static org.sonar.plugins.java.api.tree.Tree.Kind.METHOD_INVOCATION;
import static org.sonar.plugins.java.api.tree.Tree.Kind.NEW_CLASS;
import static org.sonar.plugins.java.api.tree.Tree.Kind.PLUS;
import static org.sonar.plugins.java.api.tree.Tree.Kind.PLUS_ASSIGNMENT;
import static org.sonar.ucfg.UCFGBuilder.call;
import static org.sonar.ucfg.UCFGBuilder.constant;
import static org.sonar.ucfg.UCFGBuilder.variableWithId;

public class UCFGJavaVisitor extends BaseTreeVisitor implements JavaFileScanner {
  private static final Logger LOG = Loggers.get(JavaSquid.class);
  private final File protobufDirectory;
  String javaFileKey;
  private int index = 0;

  public UCFGJavaVisitor(File workdir) {
    this.protobufDirectory = new File(new File(workdir, "ucfg"), "java");
    if (protobufDirectory.exists()) {
      // set index to the number of files, to avoid overwriting previous files
      index = protobufDirectory.list().length;
    } else {
      protobufDirectory.mkdirs();
    }
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.javaFileKey = context.getFileKey();
    if (context.getSemanticModel() == null) {
      return;
    }
    scan(context.getTree());
  }

  @Override
  public void visitMethod(MethodTree tree) {
    super.visitMethod(tree);
    Symbol.MethodSymbol methodSymbol = tree.symbol();
    List<Type> types = new ArrayList<>(methodSymbol.parameterTypes());
    if (!tree.is(CONSTRUCTOR)) {
      types.add(methodSymbol.returnType().type());
    }
    if (tree.block() != null && types.stream().noneMatch(Type::isUnknown)) {
      CFG cfg = CFG.build(tree);
      serializeUCFG(tree, cfg);
    }
  }

  @VisibleForTesting
  protected void serializeUCFG(MethodTree tree, CFG cfg) {
    try {
      UCFG uCFG = buildUCfg(tree, cfg);
      UCFGtoProtobuf.toProtobufFile(uCFG, ucfgFilePath());
    } catch (Exception e) {
      String msg = String.format("Cannot generate ucfg for file %s for method at line %d", javaFileKey, tree.firstToken().line());
      LOG.error(msg, e);
      throw new AnalysisException(msg, e);
    }
  }

  private String ucfgFilePath() {
    String absolutePath = new File(protobufDirectory, "ucfg_" + index + ".proto").getAbsolutePath();
    index++;
    return absolutePath;
  }

  private UCFG buildUCfg(MethodTree methodTree, CFG cfg) {
    String signature = signatureFor(methodTree.symbol());

    IdentifierGenerator idGenerator = new IdentifierGenerator(methodTree);
    UCFGBuilder builder = UCFGBuilder.createUCFGForMethod(signature);

    methodTree.parameters().stream()
      .map(p -> idGenerator.lookupIdFor(p.symbol()))
      .map(UCFGBuilder::variableWithId)
      .forEach(builder::addMethodParam);

    BlockBuilder entryBlockBuilder = buildBasicBlock(cfg.entry(), methodTree, idGenerator);

    if (getAnnotatedParameters(methodTree).count() > 0) {
      builder.addStartingBlock(buildParameterAnnotationsBlock(methodTree, idGenerator, cfg));
      builder.addBasicBlock(entryBlockBuilder);
    } else {
      builder.addStartingBlock(entryBlockBuilder);
    }

    cfg.blocks().stream()
      .filter(b -> !b.equals(cfg.entry()))
      .forEach(b -> builder.addBasicBlock(buildBasicBlock(b, methodTree, idGenerator)));
    return builder.build();
  }

  private BlockBuilder buildParameterAnnotationsBlock(MethodTree methodTree, IdentifierGenerator idGenerator, CFG cfg) {
    LocationInFile parametersLocation = location(methodTree.openParenToken(), methodTree.closeParenToken());
    UCFGBuilder.BlockBuilder blockBuilder = UCFGBuilder.newBasicBlock("paramAnnotations", parametersLocation);

    getAnnotatedParameters(methodTree).forEach(parameter -> buildBlockForParameter(parameter, blockBuilder, idGenerator));

    Label nextBlockLabel = UCFGBuilder.createLabel(Integer.toString(cfg.entry().id()));
    blockBuilder.jumpTo(nextBlockLabel);
    return blockBuilder;
  }

  private void buildBlockForParameter(VariableTree parameter, BlockBuilder blockBuilder, IdentifierGenerator idGenerator) {
    Expression.Variable parameterVariable = variableWithId(idGenerator.lookupIdFor(parameter.symbol()));
    List<AnnotationTree> annotationList = parameter.modifiers().annotations();
    List<Expression> annotationVariables = new ArrayList<>();

    annotationList.forEach(annotationTree -> {
      Expression.Variable var = variableWithId(idGenerator.newIdFor(annotationTree));
      annotationVariables.add(var);
      blockBuilder.assignTo(var, call(annotationTree.annotationType().symbolType().fullyQualifiedName()).withArgs(parameterVariable), location(annotationTree));
    });

    Expression[] args = annotationVariables.toArray(new Expression[annotationVariables.size()]);
    blockBuilder.assignTo(parameterVariable, call("__annotation").withArgs(args), location(parameter.simpleName()));
  }

  private static Stream<VariableTree> getAnnotatedParameters(MethodTree methodTree) {
    return methodTree.parameters().stream().filter(parameter -> isObject(parameter.type().symbolType())).filter(parameter -> !parameter.modifiers().annotations().isEmpty());
  }

  private UCFGBuilder.BlockBuilder buildBasicBlock(CFG.Block javaBlock, MethodTree methodTree, IdentifierGenerator idGenerator) {
    UCFGBuilder.BlockBuilder blockBuilder = UCFGBuilder.newBasicBlock(String.valueOf(javaBlock.id()), location(javaBlock));

    javaBlock.elements().forEach(e -> buildCall(e, blockBuilder, idGenerator));

    Tree terminator = javaBlock.terminator();
    if (terminator != null && terminator.is(Tree.Kind.RETURN_STATEMENT)) {
      ExpressionTree returnedExpression = ((ReturnStatementTree) terminator).expression();
      Expression retExpr = constant(IdentifierGenerator.CONST);
      if (methodTree.returnType() != null && isObject(methodTree.returnType().symbolType())) {
        retExpr = idGenerator.lookupExpressionFor(returnedExpression);
      }
      blockBuilder.ret(retExpr, location(terminator));
      return blockBuilder;
    }

    Set<CFG.Block> successors = javaBlock.successors();
    if (!successors.isEmpty()) {
      blockBuilder.jumpTo(successors.stream().map(b -> UCFGBuilder.createLabel(Integer.toString(b.id()))).toArray(Label[]::new));
      return blockBuilder;
    }
    Preconditions.checkState(javaBlock.id() == 0);
    blockBuilder.ret(constant("implicit return"), location(methodTree.lastToken()));
    return blockBuilder;
  }

  private void buildCall(Tree element, UCFGBuilder.BlockBuilder blockBuilder, IdentifierGenerator idGenerator) {
    if (isObjectVarDeclaration(element)) {
      VariableTree variableTree = (VariableTree) element;

      String lhs = idGenerator.lookupIdFor(variableTree.simpleName());
      if (!idGenerator.isConst(lhs)) {
        ExpressionTree initializer = variableTree.initializer();
        String source = idGenerator.lookupIdFor(initializer);
        blockBuilder.assignTo(variableWithId(lhs), UCFGBuilder.call("__id").withArgs(variableWithId(source)), location(element));
      }
      return;
    }

    if (element.is(METHOD_INVOCATION)) {
      MethodInvocationTree methodInvocationTree = (MethodInvocationTree) element;
      buildMethodInvocation(blockBuilder, idGenerator, methodInvocationTree);
    } else if (element.is(NEW_CLASS)) {
      NewClassTree newClassTree = (NewClassTree) element;
      buildConstructorInvocation(blockBuilder, idGenerator, newClassTree);
    } else if (element.is(PLUS)) {
      BinaryExpressionTree binaryExpressionTree = (BinaryExpressionTree) element;
      buildConcatenationInvocation(blockBuilder, idGenerator, binaryExpressionTree);
    } else if (element.is(ASSIGNMENT)) {
      AssignmentExpressionTree assignmentExpressionTree = (AssignmentExpressionTree) element;
      buildAssignmentInvocation(blockBuilder, idGenerator, assignmentExpressionTree);
    } else if (element.is(PLUS_ASSIGNMENT)) {
      AssignmentExpressionTree assignmentExpressionTree = (AssignmentExpressionTree) element;
      buildPlusAssignmentInvocation(blockBuilder, idGenerator, assignmentExpressionTree);
    } else if (element.is(ARRAY_ACCESS_EXPRESSION) && !element.parent().is(PLUS_ASSIGNMENT, ASSIGNMENT)) {
      // in general, an array access is a get
      Expression.Variable getValue = variableWithId(idGenerator.newId());
      Expression array = idGenerator.lookupExpressionFor(((ArrayAccessExpressionTree)element).expression());
      blockBuilder.assignTo(getValue, arrayGet(array), location(element));
      idGenerator.varForExpression(element, getValue.id());
    }
  }

  private void buildConstructorInvocation(BlockBuilder blockBuilder, IdentifierGenerator idGenerator, NewClassTree tree) {
    Symbol constructorSymbol = tree.constructorSymbol();
    if (!constructorSymbol.isMethodSymbol()) {
      return;
    }

    Expression.Variable newInstance = variableWithId(idGenerator.newIdFor(tree));
    blockBuilder.newObject(newInstance, tree.symbolType().fullyQualifiedName(), location(tree.identifier()));

    List<Expression> arguments = new ArrayList<>();
    arguments.add(newInstance);
    arguments.addAll(argumentIds(idGenerator, tree.arguments()));

    blockBuilder.assignTo(variableWithId(idGenerator.newId()),
        UCFGBuilder.call(signatureFor((Symbol.MethodSymbol) constructorSymbol)).withArgs(arguments.toArray(new Expression[0])),
        location(tree));
  }

  private void buildMethodInvocation(UCFGBuilder.BlockBuilder blockBuilder, IdentifierGenerator idGenerator, MethodInvocationTree tree) {
    if (tree.symbol().isUnknown()) {
      return;
    }

    List<Expression> arguments = new ArrayList<>();
    if (tree.symbol().isStatic()) {
      arguments.add(new Expression.ClassName(tree.symbol().type().fullyQualifiedName()));
    } else if (tree.methodSelect().is(MEMBER_SELECT)) {
      arguments.add(idGenerator.lookupExpressionFor(((MemberSelectExpressionTree) tree.methodSelect()).expression()));
    } else if (tree.methodSelect().is(IDENTIFIER)) {
      arguments.add(Expression.THIS);
    }
    arguments.addAll(argumentIds(idGenerator, tree.arguments()));

    buildAssignCall(blockBuilder, idGenerator, arguments, tree, (Symbol.MethodSymbol) tree.symbol());
  }

  private void buildConcatenationInvocation(BlockBuilder blockBuilder, IdentifierGenerator idGenerator, BinaryExpressionTree tree) {
    if (!isObject(tree.symbolType())) {
      return;
    }
    Expression leftOperand = idGenerator.lookupExpressionFor(tree.leftOperand());
    Expression rightOperand = idGenerator.lookupExpressionFor(tree.rightOperand());
    Expression.Variable var = variableWithId(idGenerator.newIdFor(tree));
    blockBuilder.assignTo(var, concat(leftOperand, rightOperand), location(tree));
  }

  private void buildAssignmentInvocation(BlockBuilder blockBuilder, IdentifierGenerator idGenerator, AssignmentExpressionTree tree) {
    if (!isObject(tree.symbolType())) {
      return;
    }
    ExpressionTree lhsTree = tree.variable();
    ExpressionTree rhsTree = tree.expression();
    Expression rightSide = lookupExpression(blockBuilder, idGenerator, rhsTree);
    if (lhsTree.is(ARRAY_ACCESS_EXPRESSION)) {
      Expression leftSide = idGenerator.lookupExpressionFor(((ArrayAccessExpressionTree)lhsTree).expression());
      // when an assignment implies both get and set on arrays, the get must be stored in an auxiliary local variable
      blockBuilder.assignTo(variableWithId(idGenerator.newId()), arraySet(leftSide, rightSide), location(tree));
    } else {
      Expression leftSide = idGenerator.lookupExpressionFor(lhsTree);
      if (leftSide instanceof  Expression.Variable) {
        blockBuilder.assignTo((Expression.Variable) leftSide, call("__id").withArgs(rightSide), location(tree));
      }
    }
  }

  private void buildPlusAssignmentInvocation(BlockBuilder blockBuilder, IdentifierGenerator idGenerator, AssignmentExpressionTree tree) {
    if (!isObject(tree.symbolType())) {
      return;
    }
    ExpressionTree lhsTree = tree.variable();
    ExpressionTree rhsTree = tree.expression();
    // '+=' is the only expression which can imply two gets and one set on an array
    Expression leftSide = lookupExpression(blockBuilder, idGenerator, lhsTree);
    Expression rightSide = lookupExpression(blockBuilder, idGenerator, rhsTree);
    if (leftSide instanceof Expression.Variable) {
      if (lhsTree.is(ARRAY_ACCESS_EXPRESSION)) {
        Expression.Variable concatAux = variableWithId(idGenerator.newId());
        blockBuilder.assignTo(concatAux, concat(leftSide, rightSide), location(tree));
        blockBuilder.assignTo(variableWithId(idGenerator.newId()),
            arraySet(idGenerator.lookupExpressionFor(((ArrayAccessExpressionTree)lhsTree).expression()), concatAux),
            location(tree));
      } else {
        idGenerator.varForExpression(tree, ((Expression.Variable) leftSide).id());
        blockBuilder.assignTo((Expression.Variable) leftSide, concat(leftSide, rightSide), location(tree));
      }
    }
  }

  private void buildAssignCall(BlockBuilder blockBuilder, IdentifierGenerator idGenerator,  List<Expression> arguments, Tree tree, Symbol.MethodSymbol symbol) {
    String destination = idGenerator.newIdFor(tree);
    blockBuilder.assignTo(variableWithId(destination), UCFGBuilder.call(signatureFor(symbol)).withArgs(arguments.toArray(new Expression[0])), location(tree));
  }

  private static List<Expression> argumentIds(IdentifierGenerator idGenerator, Arguments arguments) {
    return arguments.stream().map(idGenerator::lookupExpressionFor).collect(Collectors.toList());
  }

  /**
   * An array access expression depends on context - it might be a get or a set.
   * This method should be used for the contexts where it is clear that the array access (if present) is a get.
   */
  private Expression lookupExpression(BlockBuilder blockBuilder, IdentifierGenerator idGenerator, ExpressionTree expressionTree) {
    if (expressionTree.is(ARRAY_ACCESS_EXPRESSION)) {
      Expression array = idGenerator.lookupExpressionFor(((ArrayAccessExpressionTree)expressionTree).expression());
      Expression.Variable aux = variableWithId(idGenerator.newId());
      blockBuilder.assignTo(aux, arrayGet(array), location(expressionTree));
      return aux;
    }
    return idGenerator.lookupExpressionFor(expressionTree);
  }

  private static UCFGBuilder.CallBuilder arrayGet(Expression array) {
    return call("__arrayGet").withArgs(array);
  }

  private static UCFGBuilder.CallBuilder arraySet(Expression targetArray, Expression value) {
    return call("__arraySet").withArgs(targetArray, value);
  }

  private static UCFGBuilder.CallBuilder concat(Expression... args) {
    return call("__concat").withArgs(args);
  }

  private static String signatureFor(Symbol.MethodSymbol methodSymbol) {
    return ((JavaSymbol.MethodJavaSymbol) methodSymbol).completeSignature();
  }

  @Nullable
  private LocationInFile location(CFG.Block javaBlock) {
    Tree firstTree = null;
    List<Tree> elements = javaBlock.elements();
    if (!elements.isEmpty()) {
      firstTree = elements.get(0);
    } else if (javaBlock.terminator() != null) {
      firstTree = javaBlock.terminator();
    }
    if (firstTree == null) {
      return null;
    }
    return location(firstTree);
  }

  private LocationInFile location(Tree tree) {
    return location(tree.firstToken(), tree.lastToken());
  }

  private LocationInFile location(SyntaxToken firstToken, SyntaxToken lastToken) {
    return new LocationInFile(
      javaFileKey,
      firstToken.line(), firstToken.column(),
      lastToken.line(), lastToken.column() + lastToken.text().length()
    );
  }

  private static boolean isObjectVarDeclaration(Tree tree) {
    if (!tree.is(Tree.Kind.VARIABLE)) {
      return false;
    }

    VariableTree var = (VariableTree) tree;
    return isObject(var.type().symbolType());
  }

  private static boolean isObject(Type type) {
    return !type.isPrimitive() && !type.isUnknown();
  }

  public static class IdentifierGenerator {

    private static final String CONST = "\"\"";

    private final Map<Symbol, String> objectVars;
    private final Map<Tree, String> temps;
    private int counter;

    public IdentifierGenerator(MethodTree methodTree) {
      Set<Symbol> objectParameters = methodTree.parameters().stream().filter(p -> isObject(p.symbol().type())).map(VariableTree::symbol).collect(Collectors.toSet());
      VariableReadExtractor variableReadExtractor = new VariableReadExtractor(methodTree.symbol(), false);
      methodTree.accept(variableReadExtractor);
      Set<Symbol> objectLocals = variableReadExtractor.usedVariables().stream().filter(s -> isObject(s.type())).collect(Collectors.toSet());
      objectVars = Sets.union(objectParameters, objectLocals).stream().collect(Collectors.toMap(s -> s, Symbol::name));
      temps = new HashMap<>();
      counter = 0;
    }

    public boolean isConst(String id) {
      return CONST.equals(id);
    }

    public String newIdFor(@Nullable Tree tree) {
      return temps.computeIfAbsent(tree, t -> newId());
    }

    private String newId() {
      String result = "%" + counter;
      counter++;
      return result;
    }

    public Expression lookupExpressionFor(@Nullable Tree tree) {
      String id = lookupIdFor(tree);
      if (isConst(id)) {
        if (tree != null) {
          if (tree.is(Tree.Kind.STRING_LITERAL)) {
            return constant(LiteralUtils.trimQuotes(((LiteralTree) tree).value()));
          } else if (tree.is(IDENTIFIER) && ((IdentifierTree) tree).name().equals("this")) {
            return Expression.THIS;
          }
        }
        return constant(id);
      }
      return variableWithId(id);
    }

    public String lookupIdFor(@Nullable Tree tree) {
      if (tree == null) {
        return CONST;
      } else if (tree.is(IDENTIFIER)) {
        return lookupIdFor(((IdentifierTree) tree).symbol());
      } else {
        return temps.getOrDefault(tree, CONST);
      }
    }

    public String lookupIdFor(Symbol symbol) {
      return objectVars.getOrDefault(symbol, CONST);
    }

    public void varForExpression(Tree element, String id) {
      temps.put(element, id);
    }
  }

}
