/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
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
package org.sonar.java.checks.tests;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sonar.check.Rule;
import org.sonar.java.checks.helpers.UnitTestUtils;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S2391")
public class JUnitMethodDeclarationCheck extends IssuableSubscriptionVisitor {

  private static final String ORG_JUNIT_AFTER = "org.junit.After";
  private static final String ORG_JUNIT_BEFORE = "org.junit.Before";
  private static final String JUNIT_FRAMEWORK_TEST = "junit.framework.Test";
  private static final String JUNIT_SETUP = "setUp";
  private static final String JUNIT_TEARDOWN = "tearDown";
  private static final String JUNIT_SUITE = "suite";
  private static final int MAX_STRING_DISTANCE = 3;

  private static final Map<String, String> JUNIT4_TO_JUNIT5 = ImmutableMap
    .<String, String>builder()
    .put(ORG_JUNIT_BEFORE, "org.junit.jupiter.api.BeforeEach")
    .put("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll")
    .put(ORG_JUNIT_AFTER, "org.junit.jupiter.api.AfterEach")
    .put("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll")
    .build();
  private static final Set<String> JUNIT4_ANNOTATIONS = JUNIT4_TO_JUNIT5.keySet();
  private static final Set<String> JUNIT5_ANNOTATIONS = new HashSet<>(JUNIT4_TO_JUNIT5.values());

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Collections.singletonList(Tree.Kind.CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;

    List<MethodTree> methods = classTree.members().stream()
      .filter(member -> member.is(Tree.Kind.METHOD))
      .map(MethodTree.class::cast)
      .collect(Collectors.toList());

    int jUnitVersion = getJUnitVersion(classTree, methods);
    if (jUnitVersion > 0) {
      methods.forEach(methodTree -> checkJUnitMethod(methodTree, jUnitVersion));
    }
  }

  private static int getJUnitVersion(ClassTree classTree, List<MethodTree> methods) {
    if (isJunit3Class(classTree)) {
      return 3;
    }
    boolean containsJUnit4Tests = false;
    for (MethodTree methodTree : methods) {
      SymbolMetadata metadata = methodTree.symbol().metadata();
      containsJUnit4Tests |= metadata.isAnnotatedWith("org.junit.Test");
      if (UnitTestUtils.hasJUnit5TestAnnotation(methodTree)) {
        // While migrating from JUnit4 to JUnit5, classes might end up in mixed state of having tests using both versions.
        // If it's the case, we consider the test classes as ultimately targeting 5
        return 5;
      }
    }
    return containsJUnit4Tests ? 4 : -1;
  }

  private void checkJUnitMethod(MethodTree methodTree, int jUnitVersion) {
    String name = methodTree.simpleName().name();
    if (isSetupTearDown(name) || (jUnitVersion == 5 && isAnnotated(methodTree, ORG_JUNIT_BEFORE, ORG_JUNIT_AFTER))) {
      checkSetupTearDownSignature(methodTree, jUnitVersion);
    } else if (JUNIT_SUITE.equals(name)) {
      checkSuiteSignature(methodTree, jUnitVersion);
    } else if (jUnitVersion == 3) {
      // only check for bad naming when targeting JUnit 3
      if (methodTree.symbol().returnType().type().isSubtypeOf(JUNIT_FRAMEWORK_TEST) || areVerySimilarStrings(JUNIT_SUITE, name)) {
        addIssueForMethodBadName(methodTree, JUNIT_SUITE, name);
      } else if (areVerySimilarStrings(JUNIT_SETUP, name)) {
        addIssueForMethodBadName(methodTree, JUNIT_SETUP, name);
      } else if (areVerySimilarStrings(JUNIT_TEARDOWN, name)) {
        addIssueForMethodBadName(methodTree, JUNIT_TEARDOWN, name);
      }
    }
  }

  private static boolean isSetupTearDown(String name) {
    return JUNIT_SETUP.equals(name) || JUNIT_TEARDOWN.equals(name);
  }

  private static boolean isAnnotated(MethodTree methodTree, String... annotations) {
    SymbolMetadata methodMetadata = methodTree.symbol().metadata();
    return Arrays.stream(annotations).anyMatch(methodMetadata::isAnnotatedWith);
  }

  @VisibleForTesting
  protected boolean areVerySimilarStrings(String expected, String actual) {
    // cut complexity when the strings length difference is bigger than the accepted threshold
    return (Math.abs(expected.length() - actual.length()) <= MAX_STRING_DISTANCE)
      && StringUtils.getLevenshteinDistance(expected, actual) < MAX_STRING_DISTANCE;
  }

  private void checkSuiteSignature(MethodTree methodTree, int jUnitVersion) {
    Symbol.MethodSymbol symbol = methodTree.symbol();
    if (jUnitVersion > 3) {
      if (symbol.returnType().type().isSubtypeOf(JUNIT_FRAMEWORK_TEST)) {
        // ignore modifiers and parameters, whatever they are, "suite():Test" should be dropped in a JUnit4/5 context
        reportIssue(methodTree, String.format("Remove this method, JUnit%d test suites are not relying on it anymore.", jUnitVersion));
      }
      return;
    }
    if (!symbol.isPublic()) {
      reportIssue(methodTree, "Make this method \"public\".");
    } else if (!symbol.isStatic()) {
      reportIssue(methodTree, "Make this method \"static\".");
    } else if (!methodTree.parameters().isEmpty()) {
      reportIssue(methodTree, "This method does not accept parameters.");
    } else if (!symbol.returnType().type().isSubtypeOf(JUNIT_FRAMEWORK_TEST)) {
      reportIssue(methodTree, "This method should return either a \"junit.framework.Test\" or a \"junit.framework.TestSuite\".");
    }
  }

  private void checkSetupTearDownSignature(MethodTree methodTree, int jUnitVersion) {
    if (!methodTree.parameters().isEmpty()) {
      reportIssue(methodTree, "This method does not accept parameters.");
    } else if (jUnitVersion > 3) {
      Symbol.MethodSymbol symbol = methodTree.symbol();
      if (symbol.overriddenSymbol() != null) {
        return;
      }
      SymbolMetadata metadata = symbol.metadata();
      if (jUnitVersion == 5) {
        Optional<String> wrongJUnit4Annotation = JUNIT4_ANNOTATIONS.stream().filter(metadata::isAnnotatedWith).findFirst();
        if (wrongJUnit4Annotation.isPresent()) {
          String jUnit4Annotation = wrongJUnit4Annotation.get();
          reportIssue(methodTree, String.format("Annotate this method with JUnit5 '@%s' instead of JUnit4 '@%s'.",
            JUNIT4_TO_JUNIT5.get(jUnit4Annotation),
            jUnit4Annotation.substring(jUnit4Annotation.lastIndexOf('.') + 1)));
          return;
        }
      }
      if (JUNIT4_ANNOTATIONS.stream().anyMatch(metadata::isAnnotatedWith) || JUNIT5_ANNOTATIONS.stream().anyMatch(metadata::isAnnotatedWith)) {
        return;
      }
      reportIssue(methodTree, String.format("Annotate this method with JUnit%d '@%s' or remove it.", jUnitVersion, expectedAnnotation(symbol, jUnitVersion)));
    }
  }

  private static String expectedAnnotation(Symbol.MethodSymbol symbol, int jUnitVersion) {
    String expected;
    if (JUNIT_SETUP.equals(symbol.name())) {
      expected = ORG_JUNIT_BEFORE;
    } else {
      expected = ORG_JUNIT_AFTER;
    }
    return jUnitVersion == 4 ? expected : JUNIT4_TO_JUNIT5.get(expected);
  }

  private void addIssueForMethodBadName(MethodTree methodTree, String expected, String actual) {
    reportIssue(methodTree, "This method should be named \"" + expected + "\" not \"" + actual + "\".");
  }

  private void reportIssue(MethodTree methodTree, String message) {
    reportIssue(methodTree.simpleName(), message);
  }

  private static boolean isJunit3Class(ClassTree classTree) {
    return classTree.symbol().type().isSubtypeOf("junit.framework.TestCase");
  }

}
