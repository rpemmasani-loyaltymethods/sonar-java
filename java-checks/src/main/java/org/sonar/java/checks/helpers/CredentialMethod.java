/*
 * SonarQube Java
 * Copyright (C) 2012-2022 SonarSource SA
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

import java.util.List;
import java.util.stream.Collectors;
import org.sonar.plugins.java.api.semantic.MethodMatchers;

public class CredentialMethod {
  public final String cls;
  public final String name;
  public final List<String> args;
  private final List<Integer> indexes;

  private MethodMatchers methodMatcher;

  public CredentialMethod(String cls, String name, List<String> args, List<Integer> indexes) {
    this.cls = cls;
    this.name = name;
    this.args = args;
    this.indexes = indexes;
    this.methodMatcher = methodMatcher();
  }

  public boolean isConstructor() {
    return this.cls.endsWith(this.name);
  }

  public MethodMatchers methodMatcher() {
    if (methodMatcher != null) {
      return methodMatcher;
    }
    MethodMatchers.NameBuilder nameBuilder = MethodMatchers.create()
      .ofTypes(this.cls);

    MethodMatchers.ParametersBuilder parametersBuilder = isConstructor() ?
      nameBuilder.constructor() : nameBuilder.names(this.name);

    this.methodMatcher = parametersBuilder
      .addParametersMatcher(args.toArray(new String[0]))
      .build();
    return methodMatcher;
  }

  public List<Integer> indices() {
    return indexes.stream().map(integer -> integer - 1).collect(Collectors.toList());
  }
}
