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

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.sonar.plugins.java.api.semantic.Symbol;

import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.java.symexec.SymbolicBooleanConstraint.FALSE;
import static org.sonar.java.symexec.SymbolicBooleanConstraint.TRUE;
import static org.sonar.java.symexec.SymbolicBooleanConstraint.UNKNOWN;

public class ExecutionStateTest {

  @Test
  public void test_get_set_boolean_constraint() {
    ExecutionState state = new ExecutionState();

    Symbol.TypeSymbol ownerSymbol = mock(Symbol.TypeSymbol.class);

    Symbol.VariableSymbol fieldSymbol = mock(Symbol.VariableSymbol.class);
    when(fieldSymbol.isVariableSymbol()).thenReturn(true);
    when(fieldSymbol.owner()).thenReturn(ownerSymbol);

    // constraint for a class field is unknown by default and cannot be set.
    when(ownerSymbol.isMethodSymbol()).thenReturn(false);
    assertThat(state.getBooleanConstraint(fieldSymbol)).isSameAs(UNKNOWN);
    state.setBooleanConstraint(fieldSymbol, FALSE);
    assertThat(state.getBooleanConstraint(fieldSymbol)).isSameAs(UNKNOWN);

    // constraint for a local variable is unknown by default and can be set.
    when(ownerSymbol.isMethodSymbol()).thenReturn(true);
    assertThat(state.getBooleanConstraint(fieldSymbol)).isSameAs(UNKNOWN);
    state.setBooleanConstraint(fieldSymbol, FALSE);
    assertThat(state.getBooleanConstraint(fieldSymbol)).isSameAs(FALSE);

    // constraint for a local variable must be queried in the parent state.
    ExecutionState nestedState = new ExecutionState(state);
    assertThat(nestedState.getBooleanConstraint(fieldSymbol)).isSameAs(FALSE);

    // constraint for a local variable must shadow constraint from the parent state.
    nestedState.setBooleanConstraint(fieldSymbol, TRUE);
    assertThat(state.getBooleanConstraint(fieldSymbol)).isSameAs(FALSE);
    assertThat(nestedState.getBooleanConstraint(fieldSymbol)).isSameAs(TRUE);

    // state.setBooleanConstraint must return state
    assertThat(state.setBooleanConstraint(fieldSymbol, UNKNOWN)).isSameAs(state);
  }

  @Test
  public void test_get_set_relation() {
    Symbol.VariableSymbol leftValue = mock(Symbol.VariableSymbol.class);
    Symbol.VariableSymbol rightValue = mock(Symbol.VariableSymbol.class);

    ExecutionState state = new ExecutionState();

    // unregistered relations should evaluate to UNKNOWN.
    assertThat(state.getRelation(leftValue, rightValue)).isSameAs(SymbolicRelation.UNKNOWN);
    assertThat(state.evaluateRelation(leftValue, SymbolicRelation.UNKNOWN, rightValue)).isSameAs(UNKNOWN);

    // relations cannot be set between the same symbol.
    state.setRelation(leftValue, SymbolicRelation.GREATER_EQUAL, leftValue);
    assertThat(state.relations.size()).isEqualTo(0);

    // relations should be registered (relations are registered twice).
    state.setRelation(leftValue, SymbolicRelation.GREATER_EQUAL, rightValue);
    assertThat(state.relations.size()).isEqualTo(1 * 2);
    assertThat(state.getRelation(leftValue, rightValue)).isSameAs(SymbolicRelation.GREATER_EQUAL);
    assertThat(state.getRelation(rightValue, leftValue)).isSameAs(SymbolicRelation.LESS_EQUAL);

    // relations registered in parent state should be available in nested state.
    ExecutionState nestedState = new ExecutionState(state);
    assertThat(nestedState.relations.size()).isEqualTo(0);
    assertThat(nestedState.getRelation(leftValue, rightValue)).isSameAs(SymbolicRelation.GREATER_EQUAL);
    assertThat(nestedState.getRelation(rightValue, leftValue)).isSameAs(SymbolicRelation.LESS_EQUAL);

    // relations registered in nested state should shadow constraints in parent state (relations are registered twice).
    nestedState.setRelation(leftValue, SymbolicRelation.GREATER_THAN, rightValue);
    assertThat(nestedState.relations.size()).isEqualTo(1 * 2);
    assertThat(nestedState.getRelation(leftValue, rightValue)).isSameAs(SymbolicRelation.GREATER_THAN);
    assertThat(nestedState.getRelation(rightValue, leftValue)).isSameAs(SymbolicRelation.LESS_THAN);

    // state.setRelation must return state
    assertThat(state.setRelation(leftValue, SymbolicRelation.UNKNOWN, rightValue)).isSameAs(state);
  }

  @Test
  public void test_evaluate_relation() {
    // if one of the relation is unknown the result in unknown, regardless of the second relation.
    for (Map.Entry<SymbolicRelation, SymbolicBooleanConstraint> entry : ExecutionState.RELATION_RELATION_MAP.column(SymbolicRelation.UNKNOWN).entrySet()) {
      assertThat(entry.getValue()).isSameAs(UNKNOWN);
    }
    for (Map.Entry<SymbolicRelation, SymbolicBooleanConstraint> entry : ExecutionState.RELATION_RELATION_MAP.row(SymbolicRelation.UNKNOWN).entrySet()) {
      assertThat(entry.getValue()).isSameAs(UNKNOWN);
    }
  }

  @Test
  public void test_merge_boolean_constraints() {
    Symbol.VariableSymbol booleanFalse = mockLocalVariable();
    Symbol.VariableSymbol booleanTrue = mockLocalVariable();
    Symbol.VariableSymbol booleanBoth = mockLocalVariable();

    ExecutionState parentState = new ExecutionState();
    ExecutionState state = new ExecutionState(parentState);
    ExecutionState childState1 = new ExecutionState(state);
    ExecutionState childState21 = new ExecutionState(state);
    ExecutionState childState22 = new ExecutionState(new ExecutionState(childState21));
    ExecutionState childState31 = new ExecutionState(state);
    ExecutionState childState32 = new ExecutionState(new ExecutionState(childState31));

    // constraint must not be set if it can be retrieved from the parent state.
    parentState.setBooleanConstraint(booleanFalse, FALSE);
    childState1.setBooleanConstraint(booleanFalse, FALSE);
    childState21.setBooleanConstraint(booleanFalse, FALSE);
    childState32.setBooleanConstraint(booleanFalse, FALSE);
    state.union(ImmutableList.of(childState1, childState22, childState32));
    assertThat(state.constraints.get(booleanFalse)).isNull();
    assertThat(state.getBooleanConstraint(booleanFalse)).isSameAs(FALSE);

    // constraint must shadow constraint in parent state.
    childState1.setBooleanConstraint(booleanTrue, TRUE);
    childState21.setBooleanConstraint(booleanTrue, TRUE);
    childState32.setBooleanConstraint(booleanTrue, TRUE);
    state.union(ImmutableList.of(childState1, childState22, childState32));
    assertThat(state.getBooleanConstraint(booleanTrue)).isSameAs(TRUE);

    // union of different value must yield UNKNOWN
    childState1.setBooleanConstraint(booleanBoth, FALSE);
    childState21.setBooleanConstraint(booleanBoth, TRUE);
    childState32.setBooleanConstraint(booleanBoth, TRUE);
    state.union(ImmutableList.of(childState1, childState22, childState32));
    assertThat(state.getBooleanConstraint(booleanBoth)).isSameAs(UNKNOWN);
  }

  @Test
  public void test_merge_relations() {
    Symbol.VariableSymbol symbol11 = mockLocalVariable();
    Symbol.VariableSymbol symbol12 = mockLocalVariable();

    ExecutionState parentState = new ExecutionState();
    ExecutionState state = new ExecutionState(parentState);
    ExecutionState childState1 = new ExecutionState(state);
    ExecutionState childState21 = new ExecutionState(state);
    ExecutionState childState22 = new ExecutionState(new ExecutionState(childState21));
    ExecutionState childState31 = new ExecutionState(state);
    ExecutionState childState32 = new ExecutionState(new ExecutionState(childState31));

    parentState.setRelation(symbol11, SymbolicRelation.GREATER_THAN, symbol12);
    childState1.setRelation(symbol11, SymbolicRelation.GREATER_THAN, symbol12);
    childState21.setRelation(symbol11, SymbolicRelation.GREATER_THAN, symbol12);
    childState32.setRelation(symbol11, SymbolicRelation.GREATER_THAN, symbol12);
    state.union(ImmutableList.of(childState1, childState22, childState32));
    assertThat(state.relations.get(symbol11, symbol12)).isNull();
    assertThat(state.getRelation(symbol11, symbol12)).isEqualTo(SymbolicRelation.GREATER_THAN);
    assertThat(state.relations.get(symbol12, symbol11)).isNull();
    assertThat(state.getRelation(symbol12, symbol11)).isEqualTo(SymbolicRelation.LESS_THAN);

    parentState.setRelation(symbol11, SymbolicRelation.UNKNOWN, symbol12);
    childState1.setRelation(symbol11, SymbolicRelation.LESS_THAN, symbol12);
    childState21.setRelation(symbol11, SymbolicRelation.LESS_EQUAL, symbol12);
    childState32.setRelation(symbol11, SymbolicRelation.LESS_EQUAL, symbol12);
    state.union(ImmutableList.of(childState1, childState22, childState32));
    assertThat(state.getRelation(symbol11, symbol12)).isEqualTo(SymbolicRelation.LESS_EQUAL);
    assertThat(state.getRelation(symbol12, symbol11)).isEqualTo(SymbolicRelation.GREATER_EQUAL);
  }

  private Symbol.VariableSymbol mockLocalVariable() {
    Symbol.TypeSymbol methodSymbol = mock(Symbol.TypeSymbol.class);
    when(methodSymbol.isMethodSymbol()).thenReturn(true);

    Symbol.VariableSymbol variableSymbol = mock(Symbol.VariableSymbol.class);
    when(variableSymbol.isVariableSymbol()).thenReturn(true);
    when(variableSymbol.owner()).thenReturn(methodSymbol);
    return variableSymbol;
  }

}
