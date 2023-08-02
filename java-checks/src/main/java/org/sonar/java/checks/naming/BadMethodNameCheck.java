/*
 * SonarQube Java
 * Copyright (C) 2012-2023 SonarSource SA
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
package org.sonar.java.checks.naming;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKey;

@DeprecatedRuleKey(ruleKey = "S00100", repositoryKey = "squid")
@Rule(key = "S100")
public class BadMethodNameCheck extends IssuableSubscriptionVisitor {

  private static final String DEFAULT_FORMAT = "^[a-z][a-zA-Z0-9]*$";

  @RuleProperty(
    key = "format",
    description = "Regular expression used to check the method names against.",
    defaultValue = DEFAULT_FORMAT)
  public String format = DEFAULT_FORMAT;

  private Pattern pattern = null;

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Collections.singletonList(Tree.Kind.METHOD);
  }

  @Override
  public void setContext(JavaFileScannerContext context) {
    if (pattern == null) {
      pattern = Pattern.compile(format, Pattern.DOTALL);
    }
    super.setContext(context);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodTree methodTree = (MethodTree) tree;
    if (!isExcluded(methodTree) && !pattern.matcher(methodTree.simpleName().name()).matches()) {
      reportIssue(methodTree.simpleName(), "Rename this method name to match the regular expression '" + format + "'.");
    }
  }

  private boolean isExcluded(MethodTree methodTree) {
    return !Boolean.FALSE.equals(methodTree.isOverriding()) || isExcludedByAnnotation(methodTree);
  }

  private boolean isExcludedByAnnotation(MethodTree methodTree) {
    return methodTree.modifiers().annotations().stream().anyMatch(
      // use simple name, so we have no FP on missing semantics
      it -> "AttributeDefinition".equals(it.annotationType().symbolType().name())
    );
  }
}
