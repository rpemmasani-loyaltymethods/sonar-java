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
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.sonar.check.Rule;
import org.sonar.java.checks.helpers.ConstantUtils;
import org.sonar.java.matcher.MethodMatcher;
import org.sonar.java.matcher.MethodMatcherCollection;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S1607")
public class IgnoredTestsCheck extends IssuableSubscriptionVisitor {

  private static final String ORG_JUNIT_ASSUME = "org.junit.Assume";
  private static final String BOOLEAN_TYPE = "boolean";

  private static final MethodMatcherCollection ASSUME_METHODS = MethodMatcherCollection.create(
    MethodMatcher.create().typeDefinition(ORG_JUNIT_ASSUME).name("assumeTrue").parameters(BOOLEAN_TYPE),
    MethodMatcher.create().typeDefinition(ORG_JUNIT_ASSUME).name("assumeTrue").parameters("java.lang.String", BOOLEAN_TYPE),
    MethodMatcher.create().typeDefinition(ORG_JUNIT_ASSUME).name("assumeFalse").parameters(BOOLEAN_TYPE),
    MethodMatcher.create().typeDefinition(ORG_JUNIT_ASSUME).name("assumeFalse").parameters("java.lang.String", BOOLEAN_TYPE)
  );


  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodTree methodTree = (MethodTree) tree;
    List<SymbolMetadata.AnnotationValue> ignoreAnnotationValues = methodTree.symbol().metadata().valuesForAnnotation("org.junit.Ignore");
    if (ignoreAnnotationValues != null && ignoreAnnotationValues.isEmpty()) {
      reportIssue(methodTree.simpleName(), "Fix or remove this skipped unit test");
    }
    BlockTree block = methodTree.block();
    if(block != null) {
      block.body().stream()
        .filter(s -> s.is(Tree.Kind.EXPRESSION_STATEMENT))
        .map(s -> ((ExpressionStatementTree) s).expression())
        .filter(s -> s.is(Tree.Kind.METHOD_INVOCATION))
        .map(MethodInvocationTree.class::cast)
        .filter(ASSUME_METHODS::anyMatch)
        .filter(IgnoredTestsCheck::hasConstantOppositeArg)
        .forEach(mit -> reportIssue(mit.methodSelect(), "Fix or remove this skipped unit test"));
    }
  }

  private static boolean hasConstantOppositeArg(MethodInvocationTree mit) {
    Arguments args = mit.arguments();
    Boolean result = ConstantUtils.resolveAsBooleanConstant(args.get(args.size() - 1));
    return result != null && !result.equals(mit.symbol().name().contains("True"));
  }
}
