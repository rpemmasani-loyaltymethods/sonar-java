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
import org.sonar.java.model.SyntacticEquivalence;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Rule(
  key = "S1939",
  name = "Object should not be extended",
  tags = {"clumsy"},
  priority = Priority.MINOR)
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.UNDERSTANDABILITY)
@SqaleConstantRemediation("1min")
public class UselessExtendsCheck extends SubscriptionBaseVisitor implements JavaFileScanner {

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Kind.CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;
    TypeTree superClass = classTree.superClass();
    if (superClass != null && superClass.symbolType().is("java.lang.Object")) {
      addIssue(superClass, "\"Object\" should not be explicitly extended.");
    }
    Set<Type> interfaces = new HashSet<>();
    for (TypeTree superInterface : classTree.superInterfaces()) {
      Type interfaceType = superInterface.symbolType();
      if (interfaceType.isClass()) {
        String interfaceName = interfaceType.fullyQualifiedName();
        if (interfaces.contains(interfaceType)) {
          addIssue(superInterface, String.format("\"%s\" is listed multiple times.", interfaceName));
        } else {
          checkExtending(classTree, interfaceType, interfaceName);
        }
        interfaces.add(interfaceType);
      } else {
        checkExtending(classTree, superInterface);
      }
    }
  }

  private void checkExtending(ClassTree classTree, Type currentInterfaceType, String currentInterfaceName) {
    for (TypeTree superInterface : classTree.superInterfaces()) {
      if (!currentInterfaceType.equals(superInterface.symbolType()) && currentInterfaceType.isSubtypeOf(superInterface.symbolType())) {
        String interfaceName = superInterface.symbolType().fullyQualifiedName();
        addIssue(superInterface, String.format("\"%s\" is an \"%s\" so \"%s\" can be removed from the extension list.",
          currentInterfaceName, interfaceName, interfaceName));
      }
    }
  }

  private void checkExtending(ClassTree classTree, TypeTree currentInterfaceTree) {
    for (TypeTree superInterface : classTree.superInterfaces()) {
      if (!currentInterfaceTree.equals(superInterface) && SyntacticEquivalence.areEquivalent(currentInterfaceTree, superInterface)) {
        addIssue(superInterface, String.format("\"%s\" is listed multiple times.", extractInterfaceName(currentInterfaceTree)));
      }
    }
  }

  private String extractInterfaceName(TypeTree interfaceTree) {
    if (interfaceTree.is(Tree.Kind.IDENTIFIER)) {
      return ((IdentifierTree) interfaceTree).name();
    } else if (interfaceTree.is(Tree.Kind.MEMBER_SELECT)) {
      return ((MemberSelectExpressionTree) interfaceTree).identifier().name();
    } else if (interfaceTree.is(Tree.Kind.PARAMETERIZED_TYPE)) {
      return extractInterfaceName(((ParameterizedTypeTree) interfaceTree).type());
    }
    throw new IllegalStateException("cannot process " + interfaceTree.toString());
  }

}
