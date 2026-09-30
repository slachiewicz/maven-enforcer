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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.api.Lifecycle;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Version;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginContainer;
import org.apache.maven.api.services.LifecycleRegistry;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.EnforcerRuleUtils;
import org.apache.maven.enforcer.rules.utils.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the plugins found by the lifecycles and the packaging, which are mocked because the lifecycles are provided by
 * the Maven core.
 */
class RequirePluginVersionsLifecycleTest {

    private Project project;

    private RequirePluginVersions rule;

    private static Plugin plugin(String artifactId, String version) {
        return EnforcerTestUtils.newPlugin("org.apache.maven.plugins", artifactId, version);
    }

    private static Lifecycle lifecycle(String id, List<String> phases, Plugin... boundPlugins) {
        Lifecycle lifecycle = mock(Lifecycle.class);
        when(lifecycle.id()).thenReturn(id);
        Lifecycle.Phase phase = mock(Lifecycle.Phase.class);
        when(phase.name()).thenReturn(phases.get(0));
        when(phase.plugins()).thenReturn(Arrays.asList(boundPlugins));
        when(lifecycle.allPhases()).thenAnswer(i -> Stream.of(phase));
        when(lifecycle.aliases()).thenReturn(Collections.emptyList());
        return lifecycle;
    }

    @BeforeEach
    void setUp() {
        Session session = mock(Session.class);
        when(session.getMavenVersion()).thenReturn(mock(Version.class));
        project = mock(Project.class);

        Lifecycle clean = lifecycle("clean", Arrays.asList("pre-clean", "clean"), plugin("maven-clean-plugin", null));
        Lifecycle defaultLifecycle = lifecycle("default", Arrays.asList("compile", "package", "deploy"));
        Lifecycle site = lifecycle("site", Arrays.asList("site"), plugin("maven-site-plugin", null));

        LifecycleRegistry registry = mock(LifecycleRegistry.class);
        when(registry.iterator())
                .thenAnswer(i -> Arrays.asList(clean, defaultLifecycle, site).iterator());
        when(registry.computePhases(clean)).thenReturn(Arrays.asList("pre-clean", "clean", "post-clean"));
        when(registry.computePhases(defaultLifecycle)).thenReturn(Arrays.asList("compile", "package", "deploy"));
        when(registry.computePhases(site)).thenReturn(Arrays.asList("pre-site", "site", "post-site"));

        Map<String, PluginContainer> packagingPlugins = new LinkedHashMap<>();
        packagingPlugins.put(
                "compile",
                PluginContainer.newBuilder()
                        .plugins(Collections.singletonList(plugin("maven-compiler-plugin", null)))
                        .build());
        packagingPlugins.put(
                "package",
                PluginContainer.newBuilder()
                        .plugins(Collections.singletonList(plugin("maven-jar-plugin", null)))
                        .build());
        Packaging packaging = mock(Packaging.class);
        when(packaging.plugins()).thenReturn(packagingPlugins);
        when(session.requirePackaging("jar")).thenReturn(packaging);

        ExpressionEvaluator evaluator = mock(ExpressionEvaluator.class);
        when(evaluator.evaluate(anyString())).thenAnswer(i -> i.getArgument(0));
        EnforcerRuleUtils utils = new EnforcerRuleUtils(evaluator);

        rule = new RequirePluginVersions(session, mock(ProjectManager.class), registry, utils, project, evaluator);
        rule.setLog(mock(EnforcerLogger.class));
    }

    private void setBuildPlugins(Plugin... plugins) {
        when(project.getModel())
                .thenReturn(Model.newBuilder()
                        .packaging("jar")
                        .build(Build.newBuilder()
                                .plugins(Arrays.asList(plugins))
                                .build())
                        .build());
    }

    @Test
    void shouldFindPluginsBoundToTheLifecyclesWithoutVersion() {
        // the compiler, clean and site plugins are bound, but not declared, the jar is declared with a version
        setBuildPlugins(plugin("maven-jar-plugin", "3.4.0"));

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("Some plugins are missing valid versions")
                .hasMessageContaining("org.apache.maven.plugins:maven-compiler-plugin")
                .hasMessageContaining("org.apache.maven.plugins:maven-clean-plugin")
                .hasMessageContaining("org.apache.maven.plugins:maven-site-plugin")
                .hasMessageNotContaining("maven-jar-plugin");
    }

    @Test
    void shouldPassWhenAllBoundPluginsHaveVersions() {
        setBuildPlugins(
                plugin("maven-jar-plugin", "3.4.0"),
                plugin("maven-compiler-plugin", "3.13.0"),
                plugin("maven-clean-plugin", "3.4.0"),
                plugin("maven-site-plugin", "3.21.0"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldCheckOnlyPluginsOfTheConfiguredPhases() throws Exception {
        setBuildPlugins(plugin("maven-jar-plugin", "3.4.0"));
        java.lang.reflect.Field phases = RequirePluginVersions.class.getDeclaredField("phases");
        phases.setAccessible(true);
        phases.set(rule, "clean");

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("maven-clean-plugin")
                .hasMessageNotContaining("maven-compiler-plugin")
                .hasMessageNotContaining("maven-site-plugin");
    }

    @Test
    void shouldIgnoreUnknownPhase() throws Exception {
        setBuildPlugins(plugin("maven-jar-plugin", "3.4.0"));
        java.lang.reflect.Field phases = RequirePluginVersions.class.getDeclaredField("phases");
        phases.setAccessible(true);
        phases.set(rule, "no-such-phase");

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldMapPhasesToLifecycles() throws Exception {
        setBuildPlugins();

        Map<String, Lifecycle> map = rule.getPhaseToLifecycleMap();

        assertThat(map.get("deploy").id()).isEqualTo("default");
        assertThat(map.get("clean").id()).isEqualTo("clean");
        assertThat(map.get("site").id()).isEqualTo("site");
        assertThat(map).doesNotContainKey("unknown");
    }

    @Test
    void shouldSkipBuildPluginsWithoutLocationAsMavenDefaults() {
        // plugins from the super POM or from the lifecycle bindings have no known source, and they are not banned
        when(project.getModel())
                .thenReturn(Model.newBuilder()
                        .packaging("jar")
                        .build(Build.newBuilder()
                                .plugins(Collections.singletonList(Plugin.newBuilder()
                                        .groupId("org.apache.maven.plugins")
                                        .artifactId("maven-jar-plugin")
                                        .build()))
                                .build())
                        .build());

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("org.apache.maven.plugins:maven-jar-plugin");
    }

    @Test
    void shouldUseAdditionalPlugins() throws Exception {
        setBuildPlugins(
                plugin("maven-jar-plugin", "3.4.0"),
                plugin("maven-compiler-plugin", "3.13.0"),
                plugin("maven-clean-plugin", "3.4.0"),
                plugin("maven-site-plugin", "3.21.0"));
        java.lang.reflect.Field additional = RequirePluginVersions.class.getDeclaredField("additionalPlugins");
        additional.setAccessible(true);
        additional.set(rule, Collections.singletonList("org.apache.maven.plugins:maven-enforcer-plugin"));

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("org.apache.maven.plugins:maven-enforcer-plugin");
    }
}
