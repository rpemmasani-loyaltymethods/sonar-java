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
package org.sonar.java.model.statement;

import com.google.common.collect.Iterators;
import org.sonar.java.model.InternalSyntaxToken;
import org.sonar.java.model.JavaTree;
import org.sonar.java.model.expression.IdentifierTreeImpl;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TreeVisitor;

import javax.annotation.Nullable;

import java.util.Iterator;

public class BreakStatementTreeImpl extends JavaTree implements BreakStatementTree {
  private final InternalSyntaxToken breakToken;
  @Nullable
  private final IdentifierTree label;
  private final InternalSyntaxToken semicolonToken;

  public BreakStatementTreeImpl(InternalSyntaxToken breakToken, @Nullable IdentifierTreeImpl label, InternalSyntaxToken semicolonToken) {
    super(Kind.BREAK_STATEMENT);
    this.breakToken = breakToken;
    this.label = label;
    this.semicolonToken = semicolonToken;

    addChild(breakToken);
    if (label != null) {
      addChild(label);
    }
    addChild(semicolonToken);
  }

  @Override
  public Kind getKind() {
    return Kind.BREAK_STATEMENT;
  }

  @Override
  public SyntaxToken breakKeyword() {
    return breakToken;
  }

  @Nullable
  @Override
  public IdentifierTree label() {
    return label;
  }

  @Override
  public SyntaxToken semicolonToken() {
    return semicolonToken;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitBreakStatement(this);
  }

  @Override
  public Iterator<Tree> childrenIterator() {
    return Iterators.<Tree>concat(
      Iterators.<Tree>singletonIterator(breakToken),
      label != null ? Iterators.<Tree>singletonIterator(label) : Iterators.<Tree>emptyIterator(),
      Iterators.<Tree>singletonIterator(semicolonToken));
  }

}
