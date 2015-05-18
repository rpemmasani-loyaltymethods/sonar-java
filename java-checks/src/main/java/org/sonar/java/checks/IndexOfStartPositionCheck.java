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
import org.sonar.java.model.LiteralUtils;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import java.util.List;

@Rule(
  key = "S2912",
  name = "\"indexOf\" checks should use a start position",
  tags = {"confusing"},
  priority = Priority.MAJOR)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.UNDERSTANDABILITY)
@SqaleConstantRemediation("5min")
public class IndexOfStartPositionCheck extends SubscriptionBaseVisitor {

  private static final String JAVA_LANG_STRING = "java.lang.String";
  private static final MethodInvocationMatcher INDEX_OF_METHOD = MethodInvocationMatcher.create()
    .typeDefinition(JAVA_LANG_STRING).name("indexOf").addParameter(JAVA_LANG_STRING);

  public void scanFile(JavaFileScannerContext context) {
    super.scanFile(context);
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(
      Tree.Kind.GREATER_THAN, Tree.Kind.GREATER_THAN_OR_EQUAL_TO,
      Tree.Kind.LESS_THAN, Tree.Kind.LESS_THAN_OR_EQUAL_TO,
      Tree.Kind.EQUAL_TO, Tree.Kind.NOT_EQUAL_TO);
  }

  @Override
  public void visitNode(Tree tree) {
    checkBinaryExpression(((BinaryExpressionTree) tree));
  }

  private void checkBinaryExpression(BinaryExpressionTree expressionTree) {
    ExpressionTree leftOperand = removeParenthesis(expressionTree.leftOperand());
    ExpressionTree rightOperand = removeParenthesis(expressionTree.rightOperand());
    if (leftOperand.is(Tree.Kind.METHOD_INVOCATION)) {
      checkIndexOfInvocation((MethodInvocationTree) leftOperand, rightOperand);
    } else if (rightOperand.is(Tree.Kind.METHOD_INVOCATION)) {
      checkIndexOfInvocation((MethodInvocationTree) rightOperand, leftOperand);
    } else {
      // ignore
    }
  }

  private void checkIndexOfInvocation(MethodInvocationTree mit, ExpressionTree other) {
    if (INDEX_OF_METHOD.matches(mit)) {
      Long otherValue = LiteralUtils.longLiteralValue(other);
      if (otherValue != null && otherValue != -1 && otherValue != 0) {
        addIssue(mit, "Use \".indexOf(xxx,n) > -1\" instead.");
      }
    }
  }

  private ExpressionTree removeParenthesis(ExpressionTree expression) {
    ExpressionTree result = expression;
    while (result.is(Tree.Kind.PARENTHESIZED_EXPRESSION)) {
      result = ((ParenthesizedTree) result).expression();
    }
    return result;
  }

}
