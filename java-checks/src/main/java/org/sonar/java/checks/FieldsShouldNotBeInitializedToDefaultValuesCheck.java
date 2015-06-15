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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.semantic.Type.Primitives;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

@Rule(
  key = "S3052",
  name = "Fields should not be initialized to default values",
  tags = {"convention"},
  priority = Priority.MINOR)
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.READABILITY)
@SqaleConstantRemediation("2min")
public class FieldsShouldNotBeInitializedToDefaultValuesCheck extends SubscriptionBaseVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.CLASS, Tree.Kind.ENUM);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;
    for (Tree member : classTree.members()) {
      if (member.is(Tree.Kind.VARIABLE)) {
        checkField((VariableTree) member);
      }
    }
  }

  public void checkField(VariableTree fieldTree) {
    // No initialization or not literal => go out
    if (!isLiteral(fieldTree.initializer())) {
      return;
    }
    LiteralTree initTree = (LiteralTree) fieldTree.initializer();
    if (isInitObjectNull(initTree) || isInitNumericalZero(initTree) || isInitBooleanFalse(initTree)) {
      addIssue(fieldTree, String.format("Remove this initialization to \"%s\", the compiler will do that for you.", fieldTree.symbol().name()));
    }
  }

  private boolean isLiteral(ExpressionTree exprTree) {
    if (exprTree == null) {
      return false;
    }
    // LiteralTree kinds representation
    return exprTree.is(
      Tree.Kind.INT_LITERAL,
      Tree.Kind.LONG_LITERAL,
      Tree.Kind.FLOAT_LITERAL,
      Tree.Kind.DOUBLE_LITERAL,
      Tree.Kind.BOOLEAN_LITERAL,
      Tree.Kind.CHAR_LITERAL,
      Tree.Kind.STRING_LITERAL,
      Tree.Kind.NULL_LITERAL);
  }

  private boolean isInitObjectNull(LiteralTree initTree) {
    return initTree.is(Tree.Kind.NULL_LITERAL);
  }

  private boolean isInitNumericalZero(LiteralTree initTree) {
    try {
      return initTree.symbolType().isNumerical() && Double.doubleToRawLongBits(NumberFormat.getInstance(Locale.US).parse(initTree.value()).doubleValue()) == 0;
    } catch (ParseException e) {
      // Special case when char initialized with '\x' value. This is a numeric primitive but NumberFormat.parse throw exception
      return initTree.symbolType().isPrimitive(Primitives.CHAR) && "'\\0'".equals(initTree.value());
    }
  }

  private boolean isInitBooleanFalse(LiteralTree initTree) {
    if (initTree.symbolType().isPrimitive(Primitives.BOOLEAN) && !Boolean.parseBoolean(initTree.value())) {
      return true;
    }
    return false;
  }
}
