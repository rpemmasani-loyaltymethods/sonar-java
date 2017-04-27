/*
 * SonarQube Java
 * Copyright (C) 2012-2017 SonarSource SA
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
package org.sonar.java.se.checks;

import org.junit.Test;

import org.sonar.java.se.AlwaysTrueOrFalseExpressionCollector;
import org.sonar.java.se.CheckerContext;
import org.sonar.java.se.JavaCheckVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionAlwaysTrueOrFalseCheckTest {

  @Test
  public void test() {
    JavaCheckVerifier.verify("src/test/files/se/ConditionAlwaysTrueOrFalseCheck.java", new ConditionalUnreachableCodeCheck(), new BooleanGratuitousExpressionsCheck());
  }

  @Test
  public void test_unreachable_vs_gratuitous() {
    JavaCheckVerifier.verify("src/test/files/se/UnreachableOrGratuitous.java", new ConditionalUnreachableCodeCheck());
  }

  @Test
  public void whole_stack_required_for_ps_equality() throws Exception {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/PsEqualityRequiresFullStack.java", new AssertNoAlwaysTrueOrFalseExpression());
  }

  @Test
  public void condition_always_true_with_optional() {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/ConditionAlwaysTrueWithOptional.java", new AssertNoAlwaysTrueOrFalseExpression());
  }

  @Test
  public void resetFields_ThreadSleepCalls() throws Exception {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/ThreadSleepCall.java", new AssertNoAlwaysTrueOrFalseExpression());
  }

  @Test
  public void reporting() {
    JavaCheckVerifier.verify("src/test/files/se/ConditionAlwaysTrueOrFalseCheckReporting.java", new ConditionalUnreachableCodeCheck(), new BooleanGratuitousExpressionsCheck());
  }

  @Test
  public void reporting_getting_wrong_parent() {
    // Checks flow iterating through the correct parent
    JavaCheckVerifier.verify("src/test/files/se/ConditionAlwaysTrueOrFalseCheckParentLoop.java", new ConditionalUnreachableCodeCheck(), new BooleanGratuitousExpressionsCheck());
  }

  @Test
  public void test_transitivity() throws Exception {
    JavaCheckVerifier.verify("src/test/files/se/Transitivity.java", new ConditionalUnreachableCodeCheck(), new BooleanGratuitousExpressionsCheck());
  }

  private static class AssertNoAlwaysTrueOrFalseExpression extends SECheck {
    @Override
    public void checkEndOfExecution(CheckerContext context) {
      AlwaysTrueOrFalseExpressionCollector.AlwaysTrueOrFalseExpressions atof = context.alwaysTrueOrFalseExpressions();
      assertThat(atof.alwaysFalse()).isEmpty();
      assertThat(atof.alwaysTrue()).isEmpty();
    }
  }
}
