/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.java;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.sonar.java.model.JavaTree;
import org.sonar.java.model.JavaTree.PackageDeclarationTreeImpl;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.io.File;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

public class JavaFilesCache extends BaseTreeVisitor implements JavaFileScanner {

  @VisibleForTesting
  Map<String, File> resourcesCache = Maps.newHashMap();

  private File currentFile;
  private Deque<String> currentClassKey = new LinkedList<>();
  private Deque<Tree> parent = new LinkedList<>();
  private Deque<Integer> anonymousInnerClassCounter = new LinkedList<>();
  private String currentPackage;

  public Map<String, File> getResourcesCache() {
    return resourcesCache;
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    JavaTree.CompilationUnitTreeImpl tree = (JavaTree.CompilationUnitTreeImpl) context.getTree();
    currentPackage = PackageDeclarationTreeImpl.packageNameAsString(tree.packageDeclaration()).replace('.', '/');
    currentFile = context.getFile();
    currentClassKey.clear();
    parent.clear();
    anonymousInnerClassCounter.clear();
    scan(tree);
  }

  @Override
  public void visitClass(ClassTree tree) {
    String className = "";
    IdentifierTree simpleName = tree.simpleName();
    if (simpleName != null) {
      className = simpleName.name();
    }
    String key = getClassKey(className);
    currentClassKey.push(key);
    parent.push(tree);
    anonymousInnerClassCounter.push(0);
    resourcesCache.put(key, currentFile);
    super.visitClass(tree);
    currentClassKey.pop();
    parent.pop();
    anonymousInnerClassCounter.pop();
  }

  private String getClassKey(String className) {
    String key = className;
    if (StringUtils.isNotEmpty(currentPackage)) {
      key = currentPackage + "/" + className;
    }
    if ("".equals(className) || (parent.peek() != null && parent.peek().is(Tree.Kind.METHOD))) {
      // inner class declared within method
      int count = anonymousInnerClassCounter.pop() + 1;
      key = currentClassKey.peek() + "$" + count + className;
      anonymousInnerClassCounter.push(count);
    } else if (currentClassKey.peek() != null) {
      key = currentClassKey.peek() + "$" + className;
    }
    return key;
  }

  @Override
  public void visitMethod(MethodTree tree) {
    parent.push(tree);
    super.visitMethod(tree);
    parent.pop();
  }
}
