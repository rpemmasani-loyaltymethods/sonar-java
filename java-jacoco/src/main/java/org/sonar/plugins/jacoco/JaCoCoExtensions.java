/*
 * SonarQube Java
 * Copyright (C) 2010-2016 SonarSource SA
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
package org.sonar.plugins.jacoco;

import com.google.common.collect.ImmutableList;
import org.sonar.api.utils.Version;

import java.util.List;

public class JaCoCoExtensions {

  private JaCoCoExtensions() {
  }

  public static List getExtensions(Version sqVersion) {
    ImmutableList.Builder<Object> extensions = ImmutableList.builder();

    extensions.addAll(JacocoConstants.getPropertyDefinitions(sqVersion));
    extensions.add(
      // Unit tests
      JaCoCoSensor.class);
    if (!sqVersion.isGreaterThanOrEqual(JacocoConstants.SQ_6_2)) {
      extensions.add(
        // Integration tests
        JaCoCoItSensor.class,
        JaCoCoOverallSensor.class);
    }

    return extensions.build();
  }

}
