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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.checks.methods.MethodInvocationMatcher;
import org.sonar.java.checks.methods.TypeCriteria;
import org.sonar.java.model.JavaTree;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.CatchTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Rule(
  key = "S2095",
  name = "Resources should be closed",
  tags = {"bug", "cert", "cwe", "denial-of-service", "leak", "security"},
  priority = Priority.BLOCKER)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.LOGIC_RELIABILITY)
@SqaleConstantRemediation("5min")
public class CloseResourceCheck extends SubscriptionBaseVisitor {

  private static enum State {
    NULL, CLOSED, OPEN, IGNORED
  }

  private static final String IGNORED_CLOSEABLE_SUBTYPES[] = {
    "java.io.ByteArrayOutputStream",
    "java.io.ByteArrayInputStream",
    "java.io.StringReader",
    "java.io.StringWriter",
    "java.io.CharArraReader",
    "java.io.CharArrayWriter"
  };

  private static final String JAVA_IO_CLOSEABLE = "java.io.Closeable";
  private static final MethodInvocationMatcher CLOSE_INVOCATION = closeMethodInvocationMatcher();

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
    BlockTree block = methodTree.block();
    if (block != null) {
      CloseableVisitor closeableVisitor = new CloseableVisitor(methodTree.parameters());
      block.accept(closeableVisitor);
      for (Tree ref : closeableVisitor.unclosedCloseables()) {
        insertIssue(ref);
      }
    }
  }

  private void insertIssue(Tree tree) {
    Type type;
    if (tree.is(Tree.Kind.VARIABLE)) {
      type = ((VariableTree) tree).symbol().type();
    } else {
      type = ((IdentifierTree) tree).symbol().type();
    }
    addIssue(tree, "Close this \"" + type.name() + "\"");
  }

  private static MethodInvocationMatcher closeMethodInvocationMatcher() {
    return MethodInvocationMatcher.create()
      .typeDefinition(TypeCriteria.subtypeOf(JAVA_IO_CLOSEABLE))
      .name("close")
      .withNoParameterConstraint();
  }

  private static class CloseableVisitor extends BaseTreeVisitor {

    private ExecutionState executionState;

    public CloseableVisitor(List<VariableTree> methodParameters) {
      executionState = new ExecutionState(extractCloseableSymbols(methodParameters));
    }

    public Set<Tree> unclosedCloseables() {
      return executionState.getUnclosedClosables();
    }

    @Override
    public void visitVariable(VariableTree tree) {
      ExpressionTree initializer = tree.initializer();
      if (isRelevantCloseable(tree.symbol().type())) {
        executionState.addCloseable(tree.symbol(), tree, initializer);
      }
      executionState.checkUsageOfClosables(initializer);
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree tree) {
      ExpressionTree variable = tree.variable();
      if (variable.is(Tree.Kind.IDENTIFIER)) {
        IdentifierTree identifier = (IdentifierTree) variable;
        Symbol symbol = identifier.symbol();
        if (isRelevantCloseable(identifier.symbolType()) && symbol.owner().isMethodSymbol()) {
          executionState.addCloseable(symbol, variable, tree.expression());
        }
        executionState.checkUsageOfClosables(tree.expression());
      }
    }

    private boolean isRelevantCloseable(Type type) {
      return type.isSubtypeOf(JAVA_IO_CLOSEABLE) && !isIgnoredCloseableType(type);
    }

    private boolean isIgnoredCloseableType(Type type) {
      for (String fullyQualifiedName : IGNORED_CLOSEABLE_SUBTYPES) {
        if (type.isSubtypeOf(fullyQualifiedName)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void visitNewClass(NewClassTree tree) {
      executionState.checkUsageOfClosables(tree);
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree tree) {
      if (CLOSE_INVOCATION.matches(tree)) {
        ExpressionTree methodSelect = tree.methodSelect();
        if (methodSelect.is(Tree.Kind.MEMBER_SELECT)) {
          ExpressionTree expression = ((MemberSelectExpressionTree) methodSelect).expression();
          if (expression.is(Tree.Kind.IDENTIFIER)) {
            executionState.markAsClosed(((IdentifierTree) expression).symbol());
          }
        }
      } else {
        executionState.checkUsageOfClosables(tree);
      }
    }

    @Override
    public void visitClass(ClassTree tree) {
      // do nothing
    }

    @Override
    public void visitReturnStatement(ReturnStatementTree tree) {
      super.visitReturnStatement(tree);
      executionState.checkUsageOfClosables(tree.expression());
    }

    @Override
    public void visitTryStatement(TryStatementTree tree) {
      executionState.exclude(extractCloseableSymbols(tree.resources()));

      ExecutionState blockES = new ExecutionState(executionState);
      executionState = blockES;
      tree.block().accept(this);

      for (CatchTree catchTree : tree.catches()) {
        executionState = new ExecutionState(blockES.parentExecutionState);
        catchTree.block().accept(this);
        blockES.merge(executionState);
      }

      if (tree.finallyBlock() != null) {
        executionState = new ExecutionState(blockES.parentExecutionState);
        tree.finallyBlock().accept(this);
        blockES.overrideBy(executionState);
      }

      executionState = blockES.parentExecutionState.merge(blockES);
    }

    @Override
    public void visitIfStatement(IfStatementTree tree) {
      tree.condition().accept(this);

      ExecutionState currentES = executionState;
      ExecutionState thenES = new ExecutionState(currentES);
      ExecutionState elseES = new ExecutionState(currentES);

      executionState = thenES;
      tree.thenStatement().accept(this);

      if (tree.elseStatement() != null) {
        executionState = elseES;
        tree.elseStatement().accept(this);
      }
      executionState = currentES.merge(thenES, elseES);
    }

    private Set<Symbol> extractCloseableSymbols(List<VariableTree> variableTrees) {
      Set<Symbol> symbols = Sets.newHashSet();
      for (VariableTree variableTree : variableTrees) {
        Symbol symbol = variableTree.symbol();
        if (symbol.type().isSubtypeOf(JAVA_IO_CLOSEABLE)) {
          symbols.add(symbol);
        }
      }
      return symbols;
    }

    private static class ExecutionState {
      @Nullable
      private ExecutionState parentExecutionState;
      private Set<Symbol> excludedCloseables = Sets.newHashSet();
      private Map<Symbol, CloseableOccurence> closeableOccurenceBySymbol = Maps.newHashMap();
      private Set<Tree> unclosedCloseableReferences = Sets.newHashSet();

      ExecutionState(Set<Symbol> excludedCloseables) {
        this.excludedCloseables = excludedCloseables;
      }

      public ExecutionState(ExecutionState parentState) {
        this.excludedCloseables = parentState.excludedCloseables;
        this.parentExecutionState = parentState;
      }

      public ExecutionState merge(ExecutionState es) {
        return this.merge(es, null);
      }

      public ExecutionState merge(ExecutionState es1, @Nullable ExecutionState es2) {
        boolean useState2 = es2 != null;

        Set<Symbol> mergedSymbols = Sets.newHashSet();
        mergedSymbols.addAll(es1.closeableOccurenceBySymbol.keySet());
        if (useState2) {
          mergedSymbols.addAll(es2.closeableOccurenceBySymbol.keySet());
        }

        for (Symbol symbol : mergedSymbols) {
          CloseableOccurence currentOccurence = getCloseableOccurence(symbol);
          if (currentOccurence != null) {
            State state1 = es1.getCloseableState(symbol);
            es1.closeableOccurenceBySymbol.remove(symbol);

            State state2 = getCloseableState(symbol);
            if (useState2) {
              state2 = es2.getCloseableState(symbol);
              es2.closeableOccurenceBySymbol.remove(symbol);
            }

            // * | C | O | I | N |
            // --+---+---+---+---|
            // C | C | O | I | C | <- CLOSED
            // --+---+---+---+---|
            // O | O | O | I | O | <- OPEN
            // --+---+---+---+---|
            // I | I | I | I | I | <- IGNORED
            // --+---+---+---+---|
            // N | C | O | I | N | <- NULL
            // ------------------+

            // same state after the if statement
            if (state1.equals(state2)) {
              currentOccurence.state = state1;
            } else if ((State.CLOSED.equals(state1) && State.OPEN.equals(state2)) || (State.OPEN.equals(state1) && State.CLOSED.equals(state2))) {
              currentOccurence.state = State.OPEN;
            } else if (State.NULL.equals(state1)) {
              currentOccurence.state = state2;
            } else if (State.NULL.equals(state2)) {
              currentOccurence.state = state1;
            } else {
              currentOccurence.state = State.IGNORED;
            }

            closeableOccurenceBySymbol.put(symbol, currentOccurence);
          }
        }

        // add the closeables which could have been created but not properly closed in the context of the child ESs
        unclosedCloseableReferences.addAll(es1.getUnclosedClosables());
        if (useState2) {
          unclosedCloseableReferences.addAll(es2.getUnclosedClosables());
        }
        return this;
      }

      public ExecutionState overrideBy(ExecutionState currentES) {
        for (Entry<Symbol, CloseableOccurence> entry : currentES.closeableOccurenceBySymbol.entrySet()) {
          if (closeableOccurenceBySymbol.containsKey(entry.getKey())) {
            closeableOccurenceBySymbol.put(entry.getKey(), entry.getValue());
          }
          if (parentExecutionState != null) {
            parentExecutionState.overrideBy(currentES);
          }
        }
        return this;
      }

      private void addCloseable(Symbol symbol, Tree lastAssignmentTree, @Nullable ExpressionTree assignmentExpression) {
        if (!excludedCloseables.contains(symbol)) {
          if (isCloseableStillOpen(symbol)) {
            Tree lastAssignment = closeableOccurenceBySymbol.get(symbol).lastAssignment;
            unclosedCloseableReferences.add(lastAssignment);
          }
          closeableOccurenceBySymbol.put(symbol, new CloseableOccurence(lastAssignmentTree, getCloseableStateFromExpression(assignmentExpression)));
        }
      }

      private void checkUsageOfClosables(@Nullable ExpressionTree expression) {
        if (expression != null) {
          if (expression.is(Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS)) {
            List<ExpressionTree> arguments = Lists.newArrayList();
            if (expression.is(Tree.Kind.METHOD_INVOCATION)) {
              arguments = ((MethodInvocationTree) expression).arguments();
            } else {
              arguments = ((NewClassTree) expression).arguments();
            }
            for (ExpressionTree argument : arguments) {
              checkUsageOfClosables(argument);
            }
          } else if (expression.is(Tree.Kind.IDENTIFIER) && expression.symbolType().isSubtypeOf(JAVA_IO_CLOSEABLE)) {
            markAsIgnored(((IdentifierTree) expression).symbol());
          } else if (expression.is(Tree.Kind.MEMBER_SELECT)) {
            checkUsageOfClosables(((MemberSelectExpressionTree) expression).identifier());
          } else if (expression.is(Tree.Kind.TYPE_CAST)) {
            checkUsageOfClosables(((TypeCastTree) expression).expression());
          }
        }
      }

      private State getCloseableStateFromExpression(@Nullable ExpressionTree expression) {
        if (expression == null || expression.is(Tree.Kind.NULL_LITERAL)) {
          return State.NULL;
        } else if (expression.is(Tree.Kind.NEW_CLASS)) {
          for (ExpressionTree argument : ((NewClassTree) expression).arguments()) {
            if (argument.symbolType().isSubtypeOf(JAVA_IO_CLOSEABLE)) {
              return State.IGNORED;
            }
          }
          return State.OPEN;
        }
        return State.IGNORED;
      }

      private void markAsIgnored(Symbol symbol) {
        markAs(symbol, State.IGNORED);
      }

      private void markAsClosed(Symbol symbol) {
        markAs(symbol, State.CLOSED);
      }

      private void markAs(Symbol symbol, State state) {
        if (closeableOccurenceBySymbol.containsKey(symbol)) {
          closeableOccurenceBySymbol.get(symbol).setState(state);
        } else if (parentExecutionState != null) {
          CloseableOccurence occurence = getCloseableOccurence(symbol);
          if (occurence != null) {
            closeableOccurenceBySymbol.put(symbol, new CloseableOccurence(occurence.lastAssignment, state));
          }
        }
      }

      public void exclude(Set<Symbol> symbols) {
        excludedCloseables.addAll(symbols);
      }

      private boolean isCloseableStillOpen(Symbol symbol) {
        CloseableOccurence occurence = closeableOccurenceBySymbol.get(symbol);
        return occurence != null && State.OPEN.equals(getCloseableState(symbol));
      }

      private Set<Tree> getUnclosedClosables() {
        Set<Tree> results = Sets.newHashSet(unclosedCloseableReferences);
        for (Symbol symbol : closeableOccurenceBySymbol.keySet()) {
          if (isCloseableStillOpen(symbol)) {
            results.add(closeableOccurenceBySymbol.get(symbol).lastAssignment);
          }
        }
        return results;
      }

      @CheckForNull
      private CloseableOccurence getCloseableOccurence(Symbol symbol) {
        CloseableOccurence occurence = closeableOccurenceBySymbol.get(symbol);
        if (occurence != null) {
          return occurence;
        } else if (parentExecutionState != null) {
          return parentExecutionState.getCloseableOccurence(symbol);
        }
        return null;
      }

      @CheckForNull
      private State getCloseableState(Symbol symbol) {
        CloseableOccurence occurence = closeableOccurenceBySymbol.get(symbol);
        if (occurence != null) {
          return occurence.state;
        } else if (parentExecutionState != null) {
          return parentExecutionState.getCloseableState(symbol);
        }
        return null;
      }

      private static class CloseableOccurence {

        private Tree lastAssignment;
        private State state;

        public CloseableOccurence(Tree lastAssignment, State state) {
          this.lastAssignment = lastAssignment;
          this.state = state;
        }

        public void setState(State state) {
          this.state = state;
        }

        @Override
        public String toString() {
          JavaTree tree = (JavaTree) lastAssignment;
          return "CloseableOccurence [lastAssignment=" + tree.getName() + " (L." + tree.getLine() + "), state=" + state + "]";
        }
      }
    }
  }
}
