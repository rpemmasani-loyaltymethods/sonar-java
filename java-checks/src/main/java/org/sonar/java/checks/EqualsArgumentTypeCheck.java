/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.checks.methods.MethodInvocationMatcher;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ConditionalExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import java.util.List;

@Rule(
  key = "S2097",
  name = "\"equals(Object obj)\" should test argument type",
  tags = {"bug"},
  priority = Priority.BLOCKER)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.LOGIC_RELIABILITY)
@SqaleConstantRemediation("5min")
public class EqualsArgumentTypeCheck extends SubscriptionBaseVisitor {

  private static final MethodInvocationMatcher GETCLASS_MATCHER = MethodInvocationMatcher.create()
    .name("getClass");

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD);
  }

  @Override
  public void visitNode(Tree tree) {
    if (!hasSemantic()) {
      return;
    }
    MethodTree methodTree = (MethodTree) tree;
    if (methodTree.block() != null && "equals".equals(methodTree.symbol().name()) && methodTree.parameters().size() == 1) {
      Symbol parameterSymbol = methodTree.parameters().get(0).symbol();
      if (parameterSymbol.type().is("java.lang.Object")) {
        CastVisitor castVisitor = new CastVisitor(parameterSymbol);
        methodTree.accept(castVisitor);
        if (castVisitor.hasUncheckedCast) {
          addIssue(tree, "Add a type test to this method.");
        }
      }
    }
  }

  private static ExpressionTree removeParenthesis(ExpressionTree tree) {
    ExpressionTree result = tree;
    while (true) {
      if (result.is(Tree.Kind.PARENTHESIZED_EXPRESSION)) {
        result = ((ParenthesizedTree) result).expression();
      } else {
        return result;
      }
    }
  }

  private static boolean isArgument(ExpressionTree tree, Symbol parameterSymbol) {
    ExpressionTree expressionTree = removeParenthesis(tree);
    return expressionTree.is(Tree.Kind.IDENTIFIER) && ((IdentifierTree) expressionTree).symbol().equals(parameterSymbol);
  }

  private static class CastVisitor extends BaseTreeVisitor {
    private final Symbol parameterSymbol;
    boolean hasUncheckedCast;

    public CastVisitor(Symbol parameterSymbol) {
      this.parameterSymbol = parameterSymbol;
    }

    @Override
    public void visitBinaryExpression(BinaryExpressionTree tree) {
      if (tree.is(Tree.Kind.CONDITIONAL_AND)) {
        ExpressionVisitor expressionVisitor = new ExpressionVisitor(parameterSymbol);
        tree.leftOperand().accept(expressionVisitor);
        if (!expressionVisitor.typeChecked) {
          scan(tree.rightOperand());
        }
      } else {
        super.visitBinaryExpression(tree);
      }
    }

    @Override
    public void visitConditionalExpression(ConditionalExpressionTree tree) {
      ExpressionVisitor expressionVisitor = new ExpressionVisitor(parameterSymbol);
      tree.condition().accept(expressionVisitor);
      if (!expressionVisitor.typeChecked) {
        scan(tree.trueExpression());
      }
      scan(tree.falseExpression());
    }

    @Override
    public void visitIfStatement(IfStatementTree tree) {
      ExpressionVisitor expressionVisitor = new ExpressionVisitor(parameterSymbol);
      tree.condition().accept(expressionVisitor);
      if (!expressionVisitor.typeChecked) {
        scan(tree.thenStatement());
      }
      scan(tree.elseStatement());
    }

    @Override
    public void visitTypeCast(TypeCastTree tree) {
      if (isArgument(tree.expression(), parameterSymbol)) {
        hasUncheckedCast = true;
      } else {
        super.visitTypeCast(tree);
      }
    }
  }

  private static class ExpressionVisitor extends BaseTreeVisitor {
    private final Symbol parameterSymbol;
    private boolean typeChecked;

    ExpressionVisitor(Symbol parameterSymbol) {
      this.parameterSymbol = parameterSymbol;
    }

    @Override
    public void visitInstanceOf(InstanceOfTree tree) {
      if (isArgument(tree.expression(), parameterSymbol)) {
        typeChecked = true;
      }
    }

    @Override
    public void visitBinaryExpression(BinaryExpressionTree tree) {
      if (tree.is(Tree.Kind.EQUAL_TO) && (isGetClassOnArgument(tree.leftOperand()) || isGetClassOnArgument(tree.rightOperand()))) {
        typeChecked = true;
      } else {
        super.visitBinaryExpression(tree);
      }
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree tree) {
      if (tree.methodSelect().is(Tree.Kind.IDENTIFIER) || isSuperOrThis(((MemberSelectExpressionTree) tree.methodSelect()).expression())) {
        typeChecked = true;
      } else {
        super.visitMethodInvocation(tree);
      }
    }

    private boolean isSuperOrThis(ExpressionTree tree) {
      ExpressionTree expressionTree = removeParenthesis(tree);
      if (expressionTree.is(Tree.Kind.IDENTIFIER)) {
        String text = ((IdentifierTree) expressionTree).identifierToken().text();
        return "super".equals(text) || "this".equals(text);
      }
      return false;
    }

    private boolean isGetClassOnArgument(ExpressionTree tree) {
      ExpressionTree expressionTree = removeParenthesis(tree);
      return expressionTree.is(Tree.Kind.METHOD_INVOCATION) && GETCLASS_MATCHER.matches((MethodInvocationTree) expressionTree)
        && isInvocationOnArgument((MethodInvocationTree) expressionTree);
    }

    private boolean isInvocationOnArgument(MethodInvocationTree tree) {
      return tree.methodSelect().is(Tree.Kind.MEMBER_SELECT) && isArgument(((MemberSelectExpressionTree) tree.methodSelect()).expression(), parameterSymbol);
    }

  }

}
