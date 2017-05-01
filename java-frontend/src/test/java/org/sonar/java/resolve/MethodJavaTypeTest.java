/*
 * SonarQube Java
 * Copyright (C) 2012-2017 SonarSource SA
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
package org.sonar.java.resolve;

import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodJavaTypeTest {

  private static final Symbols SYMBOLS = new Symbols(new BytecodeCompleter(Collections.emptyList(), new ParametrizedTypeCache()));

  @Test
  public void methodJavaType_return_type() {
    MethodJavaType methodJavaType = new MethodJavaType(Collections.emptyList(), SYMBOLS.intType, Collections.emptyList(), null);
    assertThat(methodJavaType.resultType()).isSameAs(SYMBOLS.intType);

    MethodJavaType constructor = new MethodJavaType(Collections.emptyList(), null, Collections.emptyList(), null);
    assertThat(constructor.resultType()).isNull();
  }

  @Test
  public void to_string_on_type() throws Exception {
    assertThat(new JavaType(JavaType.VOID, null).toString()).isEmpty();
    String methodToString = new MethodJavaType(Collections.emptyList(), SYMBOLS.intType, Collections.emptyList(), null).toString();
    assertThat(methodToString).isEqualTo("returns int");

    String constructorToString = new MethodJavaType(Collections.emptyList(), null, Collections.emptyList(), null).toString();
    assertThat(constructorToString).isEqualTo("constructor");
  }

}
