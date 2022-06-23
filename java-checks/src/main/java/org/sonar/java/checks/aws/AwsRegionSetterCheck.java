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
package org.sonar.java.checks.aws;

import org.sonar.check.Rule;
import org.sonar.java.checks.methods.AbstractMethodDetection;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

@Rule(key = "S6262")
public class AwsRegionSetterCheck extends AbstractMethodDetection {

  private static final String STRING_TYPE = "java.lang.String";
  private static final String MESSAGE = "Give the enum value for this region instead.";

  private static final MethodMatchers REGION_SETTER_MATCHER = MethodMatchers.create()
    .ofTypes("com.amazonaws.services.s3.AmazonS3ClientBuilder")
    .names("withRegion")
    .addParametersMatcher(STRING_TYPE)
    .build();

  @Override
  protected MethodMatchers getMethodInvocationMatchers() {
    return REGION_SETTER_MATCHER;
  }

  @Override
  protected void onMethodInvocationFound(MethodInvocationTree tree) {
    Arguments arguments = tree.arguments();
    if (arguments.size() != 1) {
      return;
    }
    ExpressionTree argument = arguments.get(0);
    if (argument.is(Kind.STRING_LITERAL, Kind.IDENTIFIER)) {
      reportIssue(argument, MESSAGE);
    }
  }
}
