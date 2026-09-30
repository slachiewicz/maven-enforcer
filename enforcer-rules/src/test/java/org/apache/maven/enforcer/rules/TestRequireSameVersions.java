/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.enforcer.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginManagement;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRequireSameVersions {

    private Project project;

    private Session session;

    private RequireSameVersions rule;

    private Set<TestArtifact> deps = new HashSet<>();

    private Set<TestArtifact> plugins = new HashSet<>();

    private Set<TestArtifact> reports = new HashSet<>();

    // the plugins management and the plugins of the build which are not the plugin artifacts
    private Build build;

    @BeforeEach
    void setup() {
        project = mock(Project.class);
        session = mock(Session.class);
        rule = new RequireSameVersions(project, session);
    }

    /**
     * Sets up the model of the project and the dependencies with the artifacts and executes the rule.
     */
    private void execute() throws EnforcerRuleException {
        Node root = mock(Node.class);
        when(session.collectDependencies(eq(project), any(PathScope.class))).thenReturn(root);
        List<Node> nodes = new ArrayList<>();
        for (TestArtifact dep : deps) {
            nodes.add(DependencyNodeBuilder.node(
                    DependencyNodeBuilder.dependency(dep.groupId, dep.artifactId, dep.version, "compile", "jar", "")));
        }
        when(session.flattenDependencies(eq(root), any(PathScope.class))).thenReturn(nodes);

        List<Plugin> buildPlugins = new ArrayList<>();
        for (TestArtifact plugin : plugins) {
            buildPlugins.add(Plugin.newBuilder()
                    .groupId(plugin.groupId)
                    .artifactId(plugin.artifactId)
                    .version(plugin.version)
                    .build());
        }
        if (build != null) {
            buildPlugins.addAll(build.getPlugins());
        }
        List<ReportPlugin> reportPlugins = new ArrayList<>();
        for (TestArtifact report : reports) {
            reportPlugins.add(ReportPlugin.newBuilder()
                    .groupId(report.groupId)
                    .artifactId(report.artifactId)
                    .version(report.version.equals("RELEASE") ? null : report.version)
                    .build());
        }
        Model model = Model.newBuilder()
                .build(Build.newBuilder()
                        .plugins(buildPlugins)
                        .pluginManagement(build != null ? build.getPluginManagement() : null)
                        .build())
                .reporting(Reporting.newBuilder().plugins(reportPlugins).build())
                .build();
        when(project.getModel()).thenReturn(model);

        rule.execute();
    }

    @Test
    void testProjectWithSameVersionsInBuildAndReport() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", version);
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addBuildPlugin(extractGaString(buildPluginOne));
        rule.addBuildPlugin(extractGaString(buildPluginTwo));
        rule.addReportPlugin(extractGaString(reportPluginOne));
        rule.addReportPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).doesNotThrowAnyException();
    }

    @Test
    void testProjectWithSameVersionsInPlugins() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", version);
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addPlugin(extractGaString(buildPluginOne));
        rule.addPlugin(extractGaString(buildPluginTwo));
        rule.addPlugin(extractGaString(reportPluginOne));
        rule.addPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).doesNotThrowAnyException();
    }

    @Test
    void testProjectWithSameVersionsInBuildAndReportAndPlugins() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", version);
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addBuildPlugin(extractGaString(buildPluginOne));
        rule.addPlugin(extractGaString(buildPluginTwo));
        rule.addReportPlugin(extractGaString(reportPluginOne));
        rule.addPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).doesNotThrowAnyException();
    }

    @Test
    void testProjectWithDifferentPluginVersionsInBuildAndReport() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", "1.0.1");
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addBuildPlugin(extractGaString(buildPluginOne));
        rule.addBuildPlugin(extractGaString(buildPluginTwo));
        rule.addReportPlugin(extractGaString(reportPluginOne));
        rule.addReportPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void testProjectWithDifferentPluginVersionsInPlugins() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", "1.0.1");
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addPlugin(extractGaString(buildPluginOne));
        rule.addPlugin(extractGaString(buildPluginTwo));
        rule.addPlugin(extractGaString(reportPluginOne));
        rule.addPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void testProjectWithDifferentPluginVersionsInBuildAndReportAndPlugins() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", version);
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", "1.0.1");
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addBuildPlugin(extractGaString(buildPluginOne));
        rule.addPlugin(extractGaString(buildPluginTwo));
        rule.addReportPlugin(extractGaString(reportPluginOne));
        rule.addPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void testProjectWithDifferentDependencyVersionsInBuildAndReportAndPlugins() {
        String version = "1.0.0";
        TestArtifact dependency = constructArtifact("acme-dependency", "1.0.1");
        TestArtifact buildPluginOne = constructArtifact("acme-build-plugin-one", version);
        TestArtifact buildPluginTwo = constructArtifact("acme-build-plugin-two", version);
        TestArtifact reportPluginOne = constructArtifact("acme-report-plugin-one", version);
        TestArtifact reportPluginTwo = constructArtifact("acme-report-plugin-two", version);
        rule.addDependency(extractGaString(dependency));
        rule.addBuildPlugin(extractGaString(buildPluginOne));
        rule.addPlugin(extractGaString(buildPluginTwo));
        rule.addReportPlugin(extractGaString(reportPluginOne));
        rule.addPlugin(extractGaString(reportPluginTwo));

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dependency);
        HashSet<TestArtifact> pluginArtifacts = new HashSet<>();
        pluginArtifacts.add(buildPluginOne);
        pluginArtifacts.add(buildPluginTwo);
        HashSet<TestArtifact> reportArtifacts = new HashSet<>();
        reportArtifacts.add(reportPluginOne);
        reportArtifacts.add(reportPluginTwo);
        deps = dependencies;
        plugins = pluginArtifacts;
        reports = reportArtifacts;

        assertThatCode(this::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void testReportPluginUsesManagedVersionWhenArtifactVersionIsRelease() {
        String version = "3.5.3";

        TestArtifact buildPlugin = createArtifact("org.apache.maven.plugins", "maven-surefire-plugin", version);
        TestArtifact reportPlugin =
                createArtifact("org.apache.maven.plugins", "maven-surefire-report-plugin", "RELEASE");

        rule.addBuildPlugin(extractGaString(buildPlugin));
        rule.addReportPlugin(extractGaString(reportPlugin));

        plugins = Collections.singleton(buildPlugin);
        reports = Collections.singleton(reportPlugin);

        Plugin managedReportPlugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-surefire-report-plugin")
                .version(version)
                .build();

        build = Build.newBuilder()
                .pluginManagement(PluginManagement.newBuilder()
                        .plugins(Collections.singletonList(managedReportPlugin))
                        .build())
                .build();

        assertThatCode(this::execute).doesNotThrowAnyException();
    }

    @Test
    void testReportPluginUsesBuildPluginVersionBeforeManagedVersion() {
        String buildPluginVersion = "3.5.3";
        String managedVersion = "3.6.0";

        TestArtifact buildPlugin =
                createArtifact("org.apache.maven.plugins", "maven-surefire-plugin", buildPluginVersion);
        TestArtifact reportPlugin =
                createArtifact("org.apache.maven.plugins", "maven-surefire-report-plugin", "RELEASE");

        rule.addBuildPlugin(extractGaString(buildPlugin));
        rule.addReportPlugin(extractGaString(reportPlugin));

        plugins = Collections.singleton(buildPlugin);
        reports = Collections.singleton(reportPlugin);

        Plugin buildReportPlugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-surefire-report-plugin")
                .version(buildPluginVersion)
                .build();

        Plugin managedReportPlugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-surefire-report-plugin")
                .version(managedVersion)
                .build();

        build = Build.newBuilder()
                .plugins(Collections.singletonList(buildReportPlugin))
                .pluginManagement(PluginManagement.newBuilder()
                        .plugins(Collections.singletonList(managedReportPlugin))
                        .build())
                .build();

        assertThatCode(this::execute).doesNotThrowAnyException();
    }

    @Test
    void testReportPluginFailsWhenManagedVersionDiffersFromBuildPlugin() {
        String buildPluginVersion = "3.5.3";
        String managedVersion = "3.6.0";

        TestArtifact buildPlugin =
                createArtifact("org.apache.maven.plugins", "maven-surefire-plugin", buildPluginVersion);
        TestArtifact reportPlugin =
                createArtifact("org.apache.maven.plugins", "maven-surefire-report-plugin", "RELEASE");

        rule.addBuildPlugin(extractGaString(buildPlugin));
        rule.addReportPlugin(extractGaString(reportPlugin));

        plugins = Collections.singleton(buildPlugin);
        reports = Collections.singleton(reportPlugin);

        Plugin managedReportPlugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-surefire-report-plugin")
                .version(managedVersion)
                .build();

        build = Build.newBuilder()
                .pluginManagement(PluginManagement.newBuilder()
                        .plugins(Collections.singletonList(managedReportPlugin))
                        .build())
                .build();

        assertThatThrownBy(this::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void shouldOutputCustomMessageWhenVersionsDiffer() {
        String customMessage = "Custom same versions message";
        rule.setMessage(customMessage);

        TestArtifact dep1 = constructArtifact("acme-dep", "1.0");
        TestArtifact dep2 = constructArtifact("acme-dep2", "2.0");

        HashSet<TestArtifact> dependencies = new HashSet<>();
        dependencies.add(dep1);
        dependencies.add(dep2);

        deps = dependencies;

        rule.addDependency("org.acme:acme-dep");
        rule.addDependency("org.acme:acme-dep2");

        assertThatThrownBy(this::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageStartingWith(customMessage);
    }

    private static TestArtifact constructArtifact(String artifactId, String version) {
        return createArtifact("org.acme", artifactId, version);
    }

    private static TestArtifact createArtifact(String groupId, String artifactId, String version) {
        return new TestArtifact(groupId, artifactId, version);
    }

    private static String extractGaString(TestArtifact dependency) {
        return String.format("%s:%s", dependency.groupId, dependency.artifactId);
    }

    private static final class TestArtifact {
        private final String groupId;
        private final String artifactId;
        private final String version;

        TestArtifact(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
    }
}
