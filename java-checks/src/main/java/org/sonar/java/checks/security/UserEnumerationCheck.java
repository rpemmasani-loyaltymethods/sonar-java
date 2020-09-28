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
package org.sonar.java.checks.security;

import java.util.Arrays;
import java.util.List;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S5804")
public class UserEnumerationCheck extends IssuableSubscriptionVisitor {

  private static final String MESSAGE = "Make sure allowing user enumeration is safe here.";
  private static final String ABSTRACT_USER_DETAILS_AUTHENTICATION_PROVIDER = "org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider";
  private static final String USER_DETAILS_SERVICE = "org.springframework.security.core.userdetails.UserDetailsService";
  private static final String USERNAME_NOT_FOUND_EXCEPTION = "org.springframework.security.core.userdetails.UsernameNotFoundException";
  private static final String HIDE_USER_NOT_FOUND_EXCEPTIONS = "setHideUserNotFoundExceptions";
  private static final String LOAD_USER_BY_USERNAME = "loadUserByUsername";
  private static final String BOOLEAN = "boolean";
  private static final String STRING = "java.lang.String";
  private static final String THROWABLE = "java.lang.Throwable";

  private final MethodMatchers setHideUserMatcher = MethodMatchers.create()
    .ofSubTypes(ABSTRACT_USER_DETAILS_AUTHENTICATION_PROVIDER)
    .names(HIDE_USER_NOT_FOUND_EXCEPTIONS)
    .addParametersMatcher(BOOLEAN)
    .build();

  private final MethodMatchers loadUserMatcher = MethodMatchers.create()
    .ofSubTypes(USER_DETAILS_SERVICE)
    .names(LOAD_USER_BY_USERNAME)
    .addParametersMatcher(STRING)
    .build();

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Arrays.asList(Tree.Kind.METHOD_INVOCATION, Tree.Kind.THROW_STATEMENT);
  }

  @Override
  public void visitNode(Tree tree) {
    if (tree.is(Tree.Kind.THROW_STATEMENT)) {
      checkThrowUsernameNotFoundException(((ThrowStatementTree) tree));
      return;
    }

    MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
    Arguments arguments = methodInvocationTree.arguments();
    if (arguments.isEmpty()){
      return;
    }
    ExpressionTree expression = arguments.get(0);

    checkHiddenUserNotFoundException(methodInvocationTree, expression);

    checkLoadUserArgUsedInExceptions(methodInvocationTree, expression);
  }

  private void checkLoadUserArgUsedInExceptions(MethodInvocationTree methodInvocationTree, ExpressionTree expression) {
    if (loadUserMatcher.matches(methodInvocationTree) && expression.is(Tree.Kind.IDENTIFIER)) {
      IdentifierTree identifierTree = (IdentifierTree) expression;
      identifierTree.symbol().usages()
        .stream().filter(UserEnumerationCheck::checkParentIsThrowable)
        .forEach(value -> reportIssue(value, MESSAGE));
    }
  }

  private void checkHiddenUserNotFoundException(MethodInvocationTree methodInvocationTree, ExpressionTree expression) {
    if (setHideUserMatcher.matches(methodInvocationTree) && isFalseLiteral(expression)) {
      reportIssue(methodInvocationTree, MESSAGE);
    }
  }

  private void checkThrowUsernameNotFoundException(ThrowStatementTree tree) {
    if (tree.expression().symbolType().is(USERNAME_NOT_FOUND_EXCEPTION)) {
      reportIssue(tree, MESSAGE);
    }
  }

  private static boolean isFalseLiteral(ExpressionTree expression) {
    return expression.is(Tree.Kind.BOOLEAN_LITERAL) && !Boolean.parseBoolean(((LiteralTree) expression).value());
  }

  private static boolean checkParentIsThrowable(Tree tree) {
    Tree current = tree.parent();
    while (current instanceof ExpressionTree || current instanceof Arguments) {
      if (current.is(Tree.Kind.NEW_CLASS)) {
        NewClassTree newClassTree = (NewClassTree) current;
        if (newClassTree.symbolType().isSubtypeOf(THROWABLE)) {
          return true;
        }
      }
      current = current.parent();
    }
    return false;
  }
}
