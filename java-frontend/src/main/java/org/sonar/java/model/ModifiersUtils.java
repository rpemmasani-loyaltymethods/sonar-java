/*
 * SonarQube Java
 * Copyright (C) 2012-2024 SonarSource SA
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
package org.sonar.java.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.ModifierKeywordTree;
import org.sonar.plugins.java.api.tree.ModifiersTree;
import org.sonar.plugins.java.api.tree.PackageDeclarationTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

public final class ModifiersUtils {

  private ModifiersUtils() {
    // This class only contains static methods
  }

  public static boolean hasModifier(ModifiersTree modifiersTree, Modifier expectedModifier) {
    for (ModifierKeywordTree modifierKeywordTree : modifiersTree.modifiers()) {
      if (modifierKeywordTree.modifier() == expectedModifier) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasAll(ModifiersTree modifiersTree, Modifier... expectedModifiers) {
    for (Modifier expectedModifier : expectedModifiers) {
      if (!hasModifier(modifiersTree, expectedModifier)) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasAnyOf(ModifiersTree modifiersTree, Modifier... expectedModifiers) {
    for (Modifier expectedModifier : expectedModifiers) {
      if (hasModifier(modifiersTree, expectedModifier)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasNoneOf(ModifiersTree modifiersTree, Modifier... unexpectedModifiers) {
    return !hasAnyOf(modifiersTree, unexpectedModifiers);
  }

  @CheckForNull
  public static ModifierKeywordTree getModifier(ModifiersTree modifiers, Modifier expectedModifier) {
    return findModifier(modifiers, expectedModifier).orElse(null);
  }

  public static Optional<ModifierKeywordTree> findModifier(ModifiersTree modifiersTree,  Modifier expectedModifier) {
    return modifiersTree.modifiers().stream()
      .filter(modifierKeywordTree -> modifierKeywordTree.modifier() == expectedModifier)
      .findAny();
  }

  public static List<AnnotationTree> getAnnotations(Tree tree) {
    if (tree.kind() == Tree.Kind.VARIABLE) {
      return ((VariableTree) tree).modifiers().annotations();
    } else if (tree instanceof MethodTree methodTree) {
      return methodTree.modifiers().annotations();
    } else if (tree instanceof ClassTree classTree) {
      return classTree.modifiers().annotations();
    } else if (tree.kind() == Tree.Kind.PACKAGE) {
      return ((PackageDeclarationTree) tree).annotations();
    }
    return Collections.emptyList();
  }

}
