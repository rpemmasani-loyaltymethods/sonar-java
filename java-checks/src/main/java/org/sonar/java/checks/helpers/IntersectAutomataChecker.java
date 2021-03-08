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
package org.sonar.java.checks.helpers;

public class IntersectAutomataChecker extends AbstractAutomataChecker {
  public IntersectAutomataChecker(OrderedStatePairCache<Boolean> cache) {
    super(cache);
  }

  @Override
  protected boolean checkAuto1AndAuto2Successors(SubAutomaton auto1, SubAutomaton auto2, boolean defaultAnswer, boolean hasConsumedInput) {
    SimplifiedRegexCharacterClass characterClass1 = SimplifiedRegexCharacterClass.of(auto1.start);
    SimplifiedRegexCharacterClass characterClass2 = SimplifiedRegexCharacterClass.of(auto2.start);
    return (characterClass1 != null && characterClass2 != null) ?
      characterClass1.intersects(characterClass2, defaultAnswer) &&
        auto1.anySuccessorMatch(successor1 -> auto2.anySuccessorMatch(successor2 ->
          check(successor1, successor2, defaultAnswer, true))) :
      defaultAnswer;
  }

  @Override
  protected boolean checkAuto1Successors(SubAutomaton auto1, SubAutomaton auto2, boolean defaultAnswer, boolean hasConsumedInput) {
    return auto1.anySuccessorMatch(successor -> check(successor, auto2, defaultAnswer, hasConsumedInput));
  }

  @Override
  protected boolean checkAuto2Successors(SubAutomaton auto1, SubAutomaton auto2, boolean defaultAnswer, boolean hasConsumedInput) {
    return auto2.anySuccessorMatch(successor -> check(auto1, successor, defaultAnswer, hasConsumedInput));
  }
}
