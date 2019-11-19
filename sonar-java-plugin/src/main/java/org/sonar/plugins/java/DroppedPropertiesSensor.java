/*
 * SonarQube Java
 * Copyright (C) 2012-2019 SonarSource SA
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
package org.sonar.plugins.java;

import java.util.Arrays;
import java.util.List;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class DroppedPropertiesSensor implements Sensor {

  private static final Logger LOG = Loggers.get(DroppedPropertiesSensor.class);


  private static final String REPORT_PATHS_PROPERTY = "sonar.jacoco.reportPaths";
  private static final String REPORT_PATH_PROPERTY = "sonar.jacoco.reportPath";
  private static final String IT_REPORT_PATH_PROPERTY = "sonar.jacoco.itReportPath";
  private static final String REPORT_MISSING_FORCE_ZERO = "sonar.jacoco.reportMissing.force.zero";

  private static final List<String> REMOVED_PROPERTIES = Arrays.asList(REPORT_MISSING_FORCE_ZERO, REPORT_PATH_PROPERTY, REPORT_PATHS_PROPERTY, IT_REPORT_PATH_PROPERTY);
  private final AnalysisWarnings analysisWarnings;

  public DroppedPropertiesSensor(AnalysisWarnings analysisWarnings) {
    this.analysisWarnings = analysisWarnings;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage("java")
      .onlyWhenConfiguration(configuration -> REMOVED_PROPERTIES.stream().anyMatch(configuration::hasKey))
      .name("Removed properties sensor");
  }

  @Override
  public void execute(SensorContext context) {
    REMOVED_PROPERTIES.forEach(prop -> {
      if (context.config().hasKey(prop)) {
        String msg = "Property '" + prop + "' is no longer supported. Use JaCoCo's xml report and sonar-jacoco plugin.";
        analysisWarnings.addUnique(msg);
        LOG.warn(msg);
      }
    });
  }

}
