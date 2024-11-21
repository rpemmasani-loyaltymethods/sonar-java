/*
 * SonarQube Java
 * Copyright (C) 2013-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.samples.java;

import org.junit.Test;
import org.sonar.java.checks.verifier.CheckVerifier;

public class SubscriptionExampleCheckTest {
  @Test// Noncompliant
  public void test() {
    CheckVerifier.newVerifier().onFile("src/test/java/org/sonar/samples/java/SubscriptionExampleCheckTest.java").withCheck(new SubscriptionExampleCheck()).verifyIssues();
  }
}