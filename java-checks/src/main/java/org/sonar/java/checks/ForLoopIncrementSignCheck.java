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

import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

@Rule(
  key = "S2251",
  name = "A \"for\" loop update clause should move the counter in the right direction",
  tags = {"bug"},
  priority = Priority.BLOCKER)
@ActivatedByDefault
@SqaleSubCharacteristic(value = RulesDefinition.SubCharacteristics.INSTRUCTION_RELIABILITY)
@SqaleConstantRemediation(value = "5min")
public class  ForLoopIncrementSignCheck extends AbstractForLoopRule {

  @Override
  public void visitForStatement(ForStatementTree forStatement) {
    ExpressionTree condition = forStatement.condition();
    ForLoopIncrement loopIncrement = ForLoopIncrement.findInUpdates(forStatement);
    if (condition == null || loopIncrement == null || !loopIncrement.hasValue()) {
      return;
    }
    checkIncrementSign(condition, loopIncrement);
  }

  private void checkIncrementSign(ExpressionTree condition, ForLoopIncrement loopIncrement) {
    if (condition.is(Tree.Kind.GREATER_THAN, Tree.Kind.GREATER_THAN_OR_EQUAL_TO)) {
      BinaryExpressionTree binaryExp = (BinaryExpressionTree) condition;
      if (loopIncrement.hasSameIdentifier(binaryExp.leftOperand())) {
        checkNegativeIncrement(condition, loopIncrement);
      } else if (loopIncrement.hasSameIdentifier(binaryExp.rightOperand())) {
        checkPositiveIncrement(condition, loopIncrement);
      }
    } else if (condition.is(Tree.Kind.LESS_THAN, Tree.Kind.LESS_THAN_OR_EQUAL_TO)) {
      BinaryExpressionTree binaryExp = (BinaryExpressionTree) condition;
      if (loopIncrement.hasSameIdentifier(binaryExp.leftOperand())) {
        checkPositiveIncrement(condition, loopIncrement);
      } else if (loopIncrement.hasSameIdentifier(binaryExp.rightOperand())) {
        checkNegativeIncrement(condition, loopIncrement);
      }
    }
  }

  private void checkPositiveIncrement(Tree tree, ForLoopIncrement loopIncrement) {
    if (loopIncrement.value() < 0) {
      addIssue(tree, String.format("\"%s\" is decremented and will never reach \"stop condition\".", loopIncrement.identifier().name()));
    }
  }

  private void checkNegativeIncrement(Tree tree, ForLoopIncrement loopIncrement) {
    if (loopIncrement.value() > 0) {
      addIssue(tree, String.format("\"%s\" is incremented and will never reach \"stop condition\".", loopIncrement.identifier().name()));
    }
  }



}
