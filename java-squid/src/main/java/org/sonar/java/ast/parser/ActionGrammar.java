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
package org.sonar.java.ast.parser;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sonar.sslr.api.AstNode;
import org.sonar.java.ast.api.JavaKeyword;
import org.sonar.java.model.JavaTreeMaker;
import org.sonar.java.model.KindMaps;
import org.sonar.java.model.declaration.ModifiersTreeImpl;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.ModifiersTree;
import org.sonar.sslr.grammar.GrammarRuleKey;

import java.util.List;

public class ActionGrammar {

  // TODO Visibility
  public final GrammarBuilder b;
  public final TreeFactory f;

  public ActionGrammar(GrammarBuilder b, TreeFactory f) {
    this.b = b;
    this.f = f;
  }

  public ModifiersTree DSL_MODIFIERS() {
    return b.<ModifiersTree>nonterminal(JavaGrammar.DSL_MODIFIERS)
      .is(f.modifiers(b.zeroOrMore(b.invokeRule(JavaGrammar.MODIFIER))));
  }

  public static class TreeFactory {

    private final KindMaps kindMaps = new KindMaps();

    private final JavaTreeMaker treeMaker = new JavaTreeMaker();

    public ModifiersTree modifiers(Optional<List<AstNode>> modifierNodes) {
      if (!modifierNodes.isPresent()) {
        return ModifiersTreeImpl.EMPTY_MODIFIERS;
      }

      ImmutableList.Builder<Modifier> modifiers = ImmutableList.builder();
      ImmutableList.Builder<AnnotationTree> annotations = ImmutableList.builder();
      for (AstNode astNode : modifierNodes.get()) {
        Preconditions.checkArgument(astNode.is(JavaGrammar.MODIFIER), "Unexpected AstNodeType: %s", astNode.getType().toString());
        astNode = astNode.getFirstChild();
        if (astNode.is(JavaGrammar.ANNOTATION)) {
          annotations.add(treeMaker.annotation(astNode));
        } else {
          JavaKeyword keyword = (JavaKeyword) astNode.getType();
          modifiers.add(kindMaps.getModifier(keyword));
        }
      }

      return new ModifiersTreeImpl(modifierNodes.get(), modifiers.build(), annotations.build());
    }

  }

  public interface GrammarBuilder {

    <T> NonterminalBuilder<T> nonterminal();

    <T> NonterminalBuilder<T> nonterminal(GrammarRuleKey ruleKey);

    <T> T firstOf(T... methods);

    <T> Optional<T> optional(T method);

    <T> List<T> oneOrMore(T method);

    <T> Optional<List<T>> zeroOrMore(T method);

    AstNode invokeRule(GrammarRuleKey ruleKey);

    AstNode token(String value);

  }

  public interface NonterminalBuilder<T> {

    T is(T method);

  }

}
