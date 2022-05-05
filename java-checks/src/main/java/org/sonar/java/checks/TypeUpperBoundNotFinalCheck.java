/*
 * SonarQube Java
 * Copyright (C) 2012-2022 SonarSource SA
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
package org.sonar.java.checks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeParameterTree;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.plugins.java.api.tree.WildcardTree;

@Rule(key = "S4968")
public class TypeUpperBoundNotFinalCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Arrays.asList(Tree.Kind.TYPE_PARAMETER, Tree.Kind.EXTENDS_WILDCARD);
  }

  @Override
  public void visitNode(Tree tree) {
    if (tree.is(Tree.Kind.TYPE_PARAMETER)) {
      handleBounds(((TypeParameterTree) tree).bounds(), tree);
    } else if (tree.is(Tree.Kind.EXTENDS_WILDCARD)) {
      handleBounds(Collections.singletonList(((WildcardTree) tree).bound()), tree);
    }
  }

  private void handleBounds(List<TypeTree> bounds, Tree treeToReport) {
    for (TypeTree bound : bounds) {
      if (reportIssueIfBoundIsFinal(bound, treeToReport))
        return;
    }
  }

  private boolean reportIssueIfBoundIsFinal(TypeTree bound, Tree treeToReport) {
    if (bound.is(Tree.Kind.IDENTIFIER)) {
      if (isFinal((IdentifierTree) bound) && !isParamOfOverriddenMethod(bound)) {
        reportIssue(treeToReport, "Replace this type parametrization by the 'final' type.");
        return true;
      }
    } else if (bound.is(Tree.Kind.PARAMETERIZED_TYPE)) {
      ParameterizedTypeTree type = (ParameterizedTypeTree) bound;
      if (reportIssueIfBoundIsFinal(type.type(), treeToReport)) {
        return true;
      }
    }
    return false;
  }

  private boolean isFinal(IdentifierTree bound) {
    return bound.symbol().isFinal();
  }

  private boolean isParamOfOverriddenMethod(TypeTree bound) {
    Tree parent = bound.parent();
    while (parent != null && !parent.is(Tree.Kind.BLOCK)) {
      if (parent.is(Tree.Kind.METHOD) && Boolean.TRUE.equals(((MethodTree) parent).isOverriding())) {
        return true;
      }
      parent = parent.parent();
    }
    return false;
  }
}
