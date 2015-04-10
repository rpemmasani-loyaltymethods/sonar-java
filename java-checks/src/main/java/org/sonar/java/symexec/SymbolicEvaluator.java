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
package org.sonar.java.symexec;

import com.google.common.collect.Iterables;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.ArrayAccessExpressionTree;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.ContinueStatementTree;
import org.sonar.plugins.java.api.tree.DoWhileStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.ForEachStatement;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.LabeledStatementTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.SwitchStatementTree;
import org.sonar.plugins.java.api.tree.SynchronizedStatementTree;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WhileStatementTree;

import javax.annotation.CheckForNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SymbolicEvaluator {

  private final AssignedSymbolExtractor extractor = new AssignedSymbolExtractor();

  PackedStates evaluateCondition(ExecutionState state, ExpressionTree tree) {
    return new ConditionVisitor().evaluate(state, tree).splitUnknowns();
  }

  public PackedStates evaluateCondition(List<ExecutionState> states, ExpressionTree tree) {
    return new ConditionVisitor().evaluate(states, tree).splitUnknowns();
  }

  PackedStates evaluateExpression(ExecutionState state, ExpressionTree tree) {
    return new ExpressionVisitor().evaluate(state, tree);
  }

  PackedStates evaluateExpression(List<ExecutionState> states, ExpressionTree tree) {
    return new ExpressionVisitor().evaluate(states, tree);
  }

  public List<ExecutionState> evaluateStatement(List<ExecutionState> states, StatementTree tree) {
    return new StatementVisitor().evaluate(states, tree);
  }

  abstract class BaseExpressionVisitor extends BaseTreeVisitor {
    ExecutionState currentState;
    PackedStates currentResult;

    private PackedStates evaluate(Tree tree) {
      PackedStates oldResult = currentResult;
      PackedStates result = new PackedStates();
      currentResult = result;
      scan(tree);
      currentResult = oldResult;
      return result;
    }

    public PackedStates evaluate(ExecutionState state, Tree tree) {
      currentResult = new PackedStates();
      this.currentState = state;
      scan(tree);
      return currentResult;
    }

    public PackedStates evaluate(List<ExecutionState> states, Tree tree) {
      currentResult = new PackedStates();
      for (ExecutionState state : states) {
        this.currentState = state;
        scan(tree);
      }
      return currentResult;
    }

    public PackedStates evaluate(PackedStates states, ExpressionTree tree) {
      PackedStates oldResult = currentResult;
      ExecutionState oldState = currentState;
      PackedStates result = new PackedStates();
      currentResult = result;
      for (ExecutionState state : Iterables.concat(states.falseStates, states.trueStates, states.unknownStates)) {
        this.currentState = state;
        scan(tree);
      }
      currentResult = oldResult;
      currentState = oldState;
      return result;
    }

    @Override
    public final void visitArrayAccessExpression(ArrayAccessExpressionTree tree) {
      PackedStates result = evaluate(evaluate(tree.expression()), tree.index());
      currentResult.add(result);
    }

    @Override
    public final void visitAssignmentExpression(AssignmentExpressionTree tree) {
      PackedStates result = evaluate(evaluate(tree.variable()), tree.expression());
      result.setBooleanConstraintOnSymbol(extractLocalVariableSymbol(tree.variable()));
      currentResult.add(result);
    }

    @Override
    public final void visitBinaryExpression(BinaryExpressionTree tree) {
      if (tree.is(Tree.Kind.CONDITIONAL_AND)) {
        evaluateConditionalAnd(tree);
      } else if (tree.is(Tree.Kind.CONDITIONAL_OR)) {
        evaluateConditionalOr(tree);
      } else if (tree.is(Tree.Kind.EQUAL_TO)) {
        evaluateRelationalOperator(tree, SymbolicRelation.EQUAL_TO);
      } else if (tree.is(Tree.Kind.GREATER_THAN)) {
        evaluateRelationalOperator(tree, SymbolicRelation.GREATER_THAN);
      } else if (tree.is(Tree.Kind.GREATER_THAN_OR_EQUAL_TO)) {
        evaluateRelationalOperator(tree, SymbolicRelation.GREATER_EQUAL);
      } else if (tree.is(Tree.Kind.LESS_THAN)) {
        evaluateRelationalOperator(tree, SymbolicRelation.LESS_THAN);
      } else if (tree.is(Tree.Kind.LESS_THAN_OR_EQUAL_TO)) {
        evaluateRelationalOperator(tree, SymbolicRelation.LESS_EQUAL);
      } else if (tree.is(Tree.Kind.NOT_EQUAL_TO)) {
        evaluateRelationalOperator(tree, SymbolicRelation.NOT_EQUAL);
      }
    }

    @Override
    public final void visitInstanceOf(InstanceOfTree tree) {
      currentResult.unknownStates.add(currentState);
    }

    @Override
    public final void visitLiteral(LiteralTree tree) {
      if ("false".equals(tree.value())) {
        currentResult.falseStates.add(currentState);
      } else if ("true".equals(tree.value())) {
        currentResult.trueStates.add(currentState);
      } else {
        currentResult.unknownStates.add(currentState);
      }
    }

    @Override
    public final void visitMemberSelectExpression(MemberSelectExpressionTree tree) {
      currentResult.unknownStates.add(currentState);
    }

    @Override
    public final void visitMethodInvocation(MethodInvocationTree tree) {
      currentResult.unknownStates.add(currentState);
    }

    @Override
    public final void visitUnaryExpression(UnaryExpressionTree tree) {
      if (tree.is(Tree.Kind.LOGICAL_COMPLEMENT)) {
        PackedStates result = evaluateCondition(currentState, tree.expression());
        currentResult.falseStates.addAll(result.trueStates);
        currentResult.trueStates.addAll(result.falseStates);
        currentResult.unknownStates.addAll(result.unknownStates);
      } else {
        currentResult.unknownStates.add(currentState);
      }
    }

    private void evaluateConditionalAnd(BinaryExpressionTree tree) {
      PackedStates leftResult = evaluateCondition(currentState, tree.leftOperand());
      currentResult.falseStates.addAll(leftResult.falseStates);
      PackedStates rightResult = evaluateCondition(leftResult.trueStates, tree.rightOperand());
      currentResult.add(rightResult);
    }

    private void evaluateConditionalOr(BinaryExpressionTree tree) {
      PackedStates leftResult = evaluateCondition(currentState, tree.leftOperand());
      currentResult.trueStates.addAll(leftResult.trueStates);
      PackedStates rightResult = evaluateCondition(leftResult.falseStates, tree.rightOperand());
      currentResult.add(rightResult);
    }

    abstract void evaluateRelationalOperator(BinaryExpressionTree tree, SymbolicRelation operator);

    @CheckForNull
    final Symbol.VariableSymbol extractLocalVariableSymbol(Tree tree) {
      if (tree.is(Tree.Kind.IDENTIFIER)) {
        IdentifierTree identifierTree = (IdentifierTree) tree;
        Symbol symbol = ((IdentifierTree) identifierTree).symbol();
        if (symbol.owner().isMethodSymbol() && symbol.isVariableSymbol()) {
          return (Symbol.VariableSymbol) symbol;
        }
      }
      return null;
    }
  }

  public class ConditionVisitor extends BaseExpressionVisitor {
    @Override
    public void visitIdentifier(IdentifierTree tree) {
      Symbol.VariableSymbol symbol = extractLocalVariableSymbol(tree);
      if (symbol != null) {
        switch (currentState.getBooleanConstraint(symbol)) {
          case FALSE:
            currentResult.falseStates.add(currentState);
            return;
          case TRUE:
            currentResult.trueStates.add(currentState);
            return;
          case UNKNOWN:
            currentResult.falseStates.add(new ExecutionState(currentState).setBooleanConstraint(symbol, SymbolicBooleanConstraint.FALSE));
            currentResult.trueStates.add(new ExecutionState(currentState).setBooleanConstraint(symbol, SymbolicBooleanConstraint.TRUE));
            return;
        }
      }
      currentResult.unknownStates.add(currentState);
    }

    @Override
    void evaluateRelationalOperator(BinaryExpressionTree tree, SymbolicRelation operator) {
      Symbol.VariableSymbol leftSymbol = extractLocalVariableSymbol(tree.leftOperand());
      Symbol.VariableSymbol rightSymbol = extractLocalVariableSymbol(tree.rightOperand());
      if (leftSymbol != null && rightSymbol != null) {
        switch (currentState.evaluateRelation(leftSymbol, operator, rightSymbol)) {
          case FALSE:
            currentResult.falseStates.add(currentState);
            break;
          case TRUE:
            currentResult.trueStates.add(currentState);
            break;
          case UNKNOWN:
            currentResult.falseStates.add(new ExecutionState(currentState).setRelation(leftSymbol, operator.negate(), rightSymbol));
            currentResult.trueStates.add(new ExecutionState(currentState).setRelation(leftSymbol, operator, rightSymbol));
        }
      } else {
        currentResult.unknownStates.add(currentState);
      }
    }
  }

  public class ExpressionVisitor extends BaseExpressionVisitor {
    @Override
    public void visitIdentifier(IdentifierTree tree) {
      Symbol.VariableSymbol symbol = extractLocalVariableSymbol(tree);
      if (symbol != null) {
        switch (currentState.getBooleanConstraint(symbol)) {
          case FALSE:
            currentResult.falseStates.add(currentState);
            return;
          case TRUE:
            currentResult.trueStates.add(currentState);
            return;
          default:
            break;
        }
      }
      currentResult.unknownStates.add(currentState);
    }

    @Override
    void evaluateRelationalOperator(BinaryExpressionTree tree, SymbolicRelation operator) {
      Symbol.VariableSymbol leftSymbol = extractLocalVariableSymbol(tree.leftOperand());
      Symbol.VariableSymbol rightSymbol = extractLocalVariableSymbol(tree.rightOperand());
      if (leftSymbol != null && rightSymbol != null) {
        switch (currentState.evaluateRelation(leftSymbol, operator, rightSymbol)) {
          case FALSE:
            currentResult.falseStates.add(currentState);
            return;
          case TRUE:
            currentResult.trueStates.add(currentState);
            return;
          default:
            break;
        }
      }
      currentResult.unknownStates.add(currentState);
    }
  }

  public class StatementVisitor extends BaseTreeVisitor {
    private List<ExecutionState> currentStates;

    private List<ExecutionState> evaluate(List<ExecutionState> states, StatementTree tree) {
      currentStates = states;
      scan(tree);
      return currentStates;
    }

    @Override
    public void visitBlock(BlockTree tree) {
      for (StatementTree statement : tree.body()) {
        currentStates = evaluateStatement(currentStates, statement);
      }
    }

    @Override
    public void visitBreakStatement(BreakStatementTree tree) {
      currentStates = new ArrayList<>();
    }

    @Override
    public void visitContinueStatement(ContinueStatementTree tree) {
      currentStates = new ArrayList<>();
    }

    @Override
    public void visitDoWhileStatement(DoWhileStatementTree tree) {
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
      currentStates = evaluateStatement(currentStates, tree.statement());
      currentStates = evaluateExpression(currentStates, tree.condition()).falseStates;
    }

    @Override
    public void visitExpressionStatement(ExpressionStatementTree tree) {
      currentStates = evaluateExpression(currentStates, tree.expression()).toList();
    }

    @Override
    public void visitForStatement(ForStatementTree tree) {
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
      if (tree.condition() != null) {
        PackedStates conditionStates = evaluateCondition(currentStates, tree.condition());
        currentStates = evaluateStatement(conditionStates.trueStates, tree.statement());
        currentStates = conditionStates.falseStates;
      } else {
        currentStates = evaluateStatement(currentStates, tree.statement());
        currentStates = new ArrayList<>();
      }
    }

    @Override
    public void visitForEachStatement(ForEachStatement tree) {
      currentStates = evaluateExpression(currentStates, tree.expression()).toList();
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
      currentStates = evaluateStatement(currentStates, tree.statement());
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
    }

    @Override
    public void visitIfStatement(IfStatementTree tree) {
      PackedStates conditionStates = evaluateCondition(currentStates, tree.condition());
      List<ExecutionState> trueStates = evaluateStatement(conditionStates.trueStates, tree.thenStatement());
      List<ExecutionState> falseStates = conditionStates.falseStates;
      if (tree.elseStatement() != null) {
        falseStates = evaluateStatement(conditionStates.falseStates, tree.elseStatement());
      }
      currentStates = new ArrayList<>();
      currentStates.addAll(trueStates);
      currentStates.addAll(falseStates);
    }

    @Override
    public void visitLabeledStatement(LabeledStatementTree tree) {
      scan(tree.statement());
    }

    @Override
    public void visitReturnStatement(ReturnStatementTree tree) {
      if (tree.expression() != null) {
        evaluateExpression(currentStates, tree.expression());
      }
      currentStates = new ArrayList<>();
    }

    @Override
    public void visitSwitchStatement(SwitchStatementTree tree) {
      evaluateExpression(currentStates, tree.expression());
      // TODO: stop evaluation for now
      currentStates = new ArrayList<>();
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatementTree tree) {
      currentStates = evaluateExpression(currentStates, tree.expression()).toList();
      currentStates = evaluateStatement(currentStates, tree.block());
    }

    @Override
    public void visitThrowStatement(ThrowStatementTree tree) {
      evaluateExpression(currentStates, tree.expression()).toList();
      currentStates = new ArrayList<>();
    }

    @Override
    public void visitTryStatement(TryStatementTree tree) {
      currentStates = evaluateStatement(currentStates, tree.block());
    }

    @Override
    public void visitVariable(VariableTree tree) {
      if (tree.initializer() != null) {
        PackedStates result = evaluateExpression(currentStates, tree.initializer());
        result.setBooleanConstraintOnSymbol((Symbol.VariableSymbol) tree.symbol());
        currentStates = result.toList();
      }
    }

    @Override
    public void visitWhileStatement(WhileStatementTree tree) {
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
      PackedStates conditionStates = evaluateCondition(currentStates, tree.condition());
      evaluateStatement(conditionStates.trueStates, tree.statement());
      currentStates = conditionStates.falseStates;
      invalidateAssignedVariables(extractor.findAssignedVariables(tree));
    }

    void invalidateAssignedVariables(Set<Symbol.VariableSymbol> assignedVariables) {
      for (Symbol.VariableSymbol symbol : assignedVariables) {
        for (ExecutionState state : currentStates) {
          state.setBooleanConstraint(symbol, SymbolicBooleanConstraint.UNKNOWN);
        }
      }
    }
  }

  public static class PackedStates {
    public final List<ExecutionState> falseStates;
    public final List<ExecutionState> trueStates;
    public final List<ExecutionState> unknownStates;

    public PackedStates() {
      falseStates = new ArrayList<>();
      trueStates = new ArrayList<>();
      unknownStates = new ArrayList<>();
    }

    public PackedStates(List<ExecutionState> unknownStates) {
      falseStates = new ArrayList<>();
      trueStates = new ArrayList<>();
      this.unknownStates = unknownStates;
    }

    List<ExecutionState> toList() {
      List<ExecutionState> result = new ArrayList<>();
      result.addAll(falseStates);
      result.addAll(trueStates);
      result.addAll(unknownStates);
      return result;
    }

    void add(PackedStates that) {
      falseStates.addAll(that.falseStates);
      trueStates.addAll(that.trueStates);
      unknownStates.addAll(that.unknownStates);
    }

    void setBooleanConstraintOnSymbol(Symbol.VariableSymbol symbol) {
      if (symbol != null) {
        for (ExecutionState state : falseStates) {
          state.setBooleanConstraint(symbol, SymbolicBooleanConstraint.FALSE);
        }
        for (ExecutionState state : trueStates) {
          state.setBooleanConstraint(symbol, SymbolicBooleanConstraint.TRUE);
        }
        for (ExecutionState state : unknownStates) {
          state.setBooleanConstraint(symbol, SymbolicBooleanConstraint.UNKNOWN);
        }
      }
    }

    PackedStates splitUnknowns() {
      for (ExecutionState state : unknownStates) {
        falseStates.add(new ExecutionState(state));
        trueStates.add(new ExecutionState(state));
      }
      unknownStates.clear();
      return this;
    }

    public boolean isAlwaysFalse() {
      return !falseStates.isEmpty() && trueStates.isEmpty() && unknownStates.isEmpty();
    }

    public boolean isAlwaysTrue() {
      return !trueStates.isEmpty() && falseStates.isEmpty() && unknownStates.isEmpty();
    }

    public boolean isUnknown() {
      return !unknownStates.isEmpty() || (!trueStates.isEmpty() && !falseStates.isEmpty());
    }

    public SymbolicBooleanConstraint getBooleanConstraint() {
      if (isAlwaysFalse()) {
        return SymbolicBooleanConstraint.FALSE;
      } else if (isAlwaysTrue()) {
        return SymbolicBooleanConstraint.TRUE;
      } else {
        return SymbolicBooleanConstraint.UNKNOWN;
      }
    }
  }

}
