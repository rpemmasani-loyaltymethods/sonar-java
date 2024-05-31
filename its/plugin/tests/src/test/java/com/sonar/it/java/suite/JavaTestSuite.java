/*
 * SonarQube Java
 * Copyright (C) 2013-2024 SonarSource SA
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
package com.sonar.it.java.suite;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.sonar.java.test.classpath.TestClasspathUtils;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.Measures.Measure;
import org.sonarqube.ws.client.components.ShowRequest;
import org.sonarqube.ws.client.measures.ComponentRequest;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  JavaExtensionsTest.class,
  JavaTutorialTest.class,
  UnitTestsTest.class,
  JavaTest.class,
  JavaComplexityTest.class,
  PackageInfoTest.class,
  Struts139Test.class,
  JavaClasspathTest.class,
  SuppressWarningTest.class,
  SonarLintTest.class,
  ExternalReportTest.class,
  DuplicationTest.class
})
public class JavaTestSuite {

  public static final FileLocation JAVA_PLUGIN_LOCATION = FileLocation.of(TestClasspathUtils.findModuleJarPath("../../../sonar-java-plugin").toFile());
  public static final FileLocation TUTORIAL_EXAMPLE_PLUGIN_LOCATION = FileLocation.of(TestClasspathUtils.findModuleJarPath("../../../docs/java-custom-rules-example").toFile());

  @ClassRule
  public static final Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .useDefaultAdminCredentialsForBuilds(true)
      .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE"))
      .addPlugin(JAVA_PLUGIN_LOCATION)
      .addPlugin(FileLocation.of(TestClasspathUtils.findModuleJarPath("../../../java-symbolic-execution/java-symbolic-execution-plugin").toFile()))
      // for support of custom rules
      .addPlugin(FileLocation.of(TestUtils.pluginJar("java-extension-plugin")))
      // making sure the tutorial is still working
      .addPlugin(TUTORIAL_EXAMPLE_PLUGIN_LOCATION)
      // profiles for each test projects
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-java-extension.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-java-tutorial.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-java-version-aware-visitor.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-dit.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-ignored-test.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-java-complexity.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-filtered-issues.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-using-aar-dep.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-package-info.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-package-info-annotations.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-suppress-warnings.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-suppress-warnings-pmd.xml"))
      .restoreProfileAtStartup(FileLocation.ofClasspath("/profile-depends-on-jdk-types.xml"));
    ORCHESTRATOR = orchestratorBuilder.build();
  }

  public static String keyFor(String projectKey, String pkgDir, String cls) {
    return projectKey + ":src/main/java/" + pkgDir + cls;
  }

  @CheckForNull
  static Measure getMeasure(String componentKey, String metricKey) {
    Measures.ComponentWsResponse response = TestUtils.newWsClient(ORCHESTRATOR).measures().component(new ComponentRequest()
      .setComponent(componentKey)
      .setMetricKeys(singletonList(metricKey)));
    List<Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  static Map<String, Measure> getMeasures(String componentKey, String... metricKeys) {
    return TestUtils.newWsClient(ORCHESTRATOR).measures().component(new ComponentRequest()
      .setComponent(componentKey)
      .setMetricKeys(asList(metricKeys)))
      .getComponent().getMeasuresList()
      .stream()
      .collect(Collectors.toMap(Measure::getMetric, Function.identity()));
  }

  @CheckForNull
  static Integer getMeasureAsInteger(String componentKey, String metricKey) {
    Measure measure = getMeasure(componentKey, metricKey);
    return (measure == null) ? null : Integer.parseInt(measure.getValue());
  }

  @CheckForNull
  static Double getMeasureAsDouble(String componentKey, String metricKey) {
    Measure measure = getMeasure(componentKey, metricKey);
    return (measure == null) ? null : Double.parseDouble(measure.getValue());
  }

  static Component getComponent(String componentKey) {
    return TestUtils.newWsClient(ORCHESTRATOR).components().show(new ShowRequest().setComponent(componentKey)).getComponent();
  }

}
