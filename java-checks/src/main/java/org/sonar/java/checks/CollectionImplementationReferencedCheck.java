/*
 * SonarQube Java
 * Copyright (C) 2012-2021 SonarSource SA
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

import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.java.collections.MapBuilder;
import org.sonar.java.model.ModifiersUtils;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.ModifiersTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;

@Rule(key = "S1319")
public class CollectionImplementationReferencedCheck extends BaseTreeVisitor implements JavaFileScanner {

  private static final String DEQUE = "java.util.Deque";
  private static final String LIST = "java.util.List";
  private static final String MAP = "java.util.Map";
  private static final String CONCURRENT_MAP = "java.util.concurrent.ConcurrentMap";
  private static final String QUEUE = "java.util.Queue";
  private static final String SET = "java.util.Set";
  private static final String SORTED_MAP = "java.util.SortedMap";
  private static final String SORTED_SET = "java.util.SortedSet";

  private static final Map<String, String> MAPPING = MapBuilder.<String, String> newMap()
    .put("java.util.ArrayDeque", DEQUE)
    .put("java.util.concurrent.ConcurrentLinkedDeque", DEQUE)

    .put("java.util.AbstractList", LIST)
    .put("java.util.AbstractSequentialList", LIST)
    .put("java.util.ArrayList", LIST)
    .put("java.util.LinkedList", LIST)
    .put("java.util.concurrent.CopyOnWriteArrayList", LIST)

    .put("java.util.AbstractMap", MAP)
    .put("java.util.EnumMap", MAP)
    .put("java.util.HashMap", MAP)
    .put("java.util.Hashtable", MAP)
    .put("java.util.IdentityHashMap", MAP)
    .put("java.util.LinkedHashMap", MAP)
    .put("java.util.WeakHashMap", MAP)

    .put("java.util.concurrent.ConcurrentHashMap", CONCURRENT_MAP)
    .put("java.util.concurrent.ConcurrentSkipListMap", CONCURRENT_MAP)

    .put("java.util.AbstractQueue", QUEUE)
    .put("java.util.concurrent.ConcurrentLinkedQueue", QUEUE)
    .put("java.util.concurrent.SynchronousQueue", QUEUE)

    .put("java.util.AbstractSet", SET)
    .put("java.util.concurrent.CopyOnWriteArraySet", SET)
    .put("java.util.EnumSet", SET)
    .put("java.util.HashSet", SET)
    .put("java.util.LinkedHashSet", SET)

    .put("java.util.TreeMap", SORTED_MAP)

    .put("java.util.TreeSet", SORTED_SET)
    .build();

  private JavaFileScannerContext context;

  @Override
  public void scanFile(final JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitVariable(VariableTree tree) {
    super.visitVariable(tree);
    if (isPublic(tree.modifiers())) {
      checkIfAllowed(tree.type(), String.format("The type of \"%s\"", tree.simpleName()));
    }
  }

  @Override
  public void visitMethod(MethodTree tree) {
    super.visitMethod(tree);
    if (isPublic(tree.modifiers()) && Boolean.FALSE.equals(tree.isOverriding())) {
      checkIfAllowed(tree.returnType(), "The return type of this method");
      for (VariableTree variableTree : tree.parameters()) {
        checkIfAllowed(variableTree.type(), String.format("The type of \"%s\"", variableTree.simpleName()));
      }
    }
  }

  private void checkIfAllowed(@Nullable TypeTree tree, String messagePrefix) {
    if (tree == null) {
      return;
    }

    String collectionImplementation = tree.symbolType().erasure().fullyQualifiedName();
    if (!MAPPING.containsKey(collectionImplementation)) {
      return;
    }

    if (tree.is(Tree.Kind.PARAMETERIZED_TYPE)) {
      tree = ((ParameterizedTypeTree) tree).type();
    }

    String message = String.format("%s should be an interface such as \"%s\" rather than the implementation \"%s\".",
      messagePrefix,
      toSimpleName(MAPPING.get(collectionImplementation)),
      toSimpleName(collectionImplementation));
    context.reportIssue(this, tree, message);
  }

  private static String toSimpleName(String fullyQualifiedName) {
    return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
  }

  private static boolean isPublic(ModifiersTree modifiers) {
    return ModifiersUtils.hasModifier(modifiers, Modifier.PUBLIC);
  }
}
