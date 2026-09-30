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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.services.LifecycleRegistry;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rules.utils.EnforcerRuleUtils;
import org.apache.maven.enforcer.rules.utils.ExpressionEvaluator;
import org.apache.maven.enforcer.rules.utils.PluginWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * The Class TestRequirePluginVersions.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@ExtendWith(MockitoExtension.class)
class TestRequirePluginVersions {

    @Mock
    private Session session;

    @Mock
    private ProjectManager projectManager;

    @Mock
    private LifecycleRegistry lifecycleRegistry;

    @Mock
    private EnforcerRuleUtils utils;

    @Mock
    private Project project;

    @Mock
    private ExpressionEvaluator evaluator;

    @InjectMocks
    private RequirePluginVersions rule;

    @BeforeEach
    void setup() {
        rule.setLog(Mockito.mock(EnforcerLogger.class));
    }

    /**
     * Test has version specified.
     */
    @Test
    void testHasVersionSpecified() throws Exception {

        when(evaluator.evaluate(anyString())).thenAnswer(i -> i.getArgument(0));
        when(evaluator.evaluate(isNull())).thenReturn(null);

        Plugin source = Plugin.newBuilder().groupId("group").artifactId("foo").build();

        // set up the plugins. I'm setting up the foo group
        // with a few bogus entries and then a real one.
        // to test that the list is exhaustively
        // searched for versions before giving up.
        // banLatest/Release will fail if it is found
        // anywhere in the list.
        List<Plugin> plugins = new ArrayList<>();
        add(plugins, EnforcerTestUtils.newPlugin("group", "a-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo", null));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo", ""));
        add(plugins, EnforcerTestUtils.newPlugin("group", "b-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "c-artifact", "LATEST"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "c-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "d-artifact", "RELEASE"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "d-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "e-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "e-artifact", "RELEASE"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "f-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "f-artifact", "LATEST"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "f-artifact", "1.0-SNAPSHOT"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "g-artifact", "1.0-12345678.123456-1"));

        List<PluginWrapper> pluginWrappers = PluginWrapper.addAll(plugins, false);

        rule.setBanLatest(false);
        rule.setBanRelease(false);
        rule.setBanSnapshots(false);

        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that LATEST is allowed
        source = source.withArtifactId("c-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that LATEST is banned
        rule.setBanLatest(true);
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that LATEST is exhaustively checked
        rule.setBanSnapshots(false);
        source = source.withArtifactId("f-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        rule.setBanLatest(false);
        rule.setBanSnapshots(true);
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that TIMESTAMP is allowed
        rule.setBanTimestamps(false);
        source = source.withArtifactId("g-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that RELEASE is allowed
        source = source.withArtifactId("d-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that RELEASE is banned
        rule.setBanRelease(true);
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        // check that RELEASE is exhaustively checked
        source = source.withArtifactId("e-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));
    }

    /**
     * Test has version specified with properties.
     */
    @Test
    void testHasVersionSpecifiedWithProperties() throws Exception {

        when(evaluator.evaluate(anyString())).thenAnswer(i -> ((String) i.getArgument(0)).replaceAll("\\$\\{|}", ""));

        Plugin source = Plugin.newBuilder().groupId("group").build();

        // set up the plugins.
        List<Plugin> plugins = new ArrayList<>();
        add(plugins, EnforcerTestUtils.newPlugin("group", "a-artifact", "1.0-${SNAPSHOT}"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "b-artifact", "${1.0}"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "c-artifact", "${LATEST}"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "d-artifact", "${RELEASE}"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "e-artifact", "${}"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "f-artifact", "${   }"));

        List<PluginWrapper> pluginWrappers = PluginWrapper.addAll(plugins, false);

        rule.setBanLatest(false);
        rule.setBanRelease(false);
        rule.setBanSnapshots(false);

        source = source.withArtifactId("a-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        source = source.withArtifactId("b-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        source = source.withArtifactId("c-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        source = source.withArtifactId("d-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));

        // this one checks empty property values
        source = source.withArtifactId("e-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        // this one checks empty property values
        source = source.withArtifactId("f-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        rule.setBanLatest(true);
        source = source.withArtifactId("c-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        rule.setBanRelease(true);
        source = source.withArtifactId("d-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        rule.setBanSnapshots(true);
        source = source.withArtifactId("a-artifact");
        assertFalse(rule.hasValidVersionSpecified(source, pluginWrappers));

        // release versions should pass everything
        source = source.withArtifactId("b-artifact");
        assertTrue(rule.hasValidVersionSpecified(source, pluginWrappers));
    }

    /**
     * Test get additional plugins null.
     *
     */
    @Test
    void testGetAdditionalPluginsNull() throws Exception {
        rule.addAdditionalPlugins(null, null);
    }

    /**
     * Test get additional plugins invalid format.
     */
    @Test
    void testGetAdditionalPluginsInvalidFormat() {

        List<String> additional = new ArrayList<>();

        // invalid format (not enough sections)
        additional.add("group");

        Map<String, Plugin> plugins = new LinkedHashMap<>();
        try {
            rule.addAdditionalPlugins(plugins, additional);
            fail("Expected Exception because the format is invalid");
        } catch (EnforcerRuleError e) {
        }

        // invalid format (too many sections)
        additional.clear();
        additional.add("group:i:i");
        try {
            rule.addAdditionalPlugins(plugins, additional);
            fail("Expected Exception because the format is invalid");
        } catch (EnforcerRuleError e) {
        }
    }

    /**
     * Test get additional plugins empty set.
     *
     */
    @Test
    void testGetAdditionalPluginsEmptySet() throws Exception {
        List<String> additional = new ArrayList<>();
        additional.add("group:a-artifact");
        additional.add("group:another-artifact");

        // make sure a null set can be handled
        Map<String, Plugin> results = rule.addAdditionalPlugins(null, additional);

        assertNotNull(results);
        assertContainsPlugin("group", "a-artifact", results);
        assertContainsPlugin("group", "another-artifact", results);
    }

    /**
     * Test get additional plugins.
     *
     */
    @Test
    void testGetAdditionalPlugins() throws Exception {

        Map<String, Plugin> plugins = new LinkedHashMap<>();
        add(plugins, EnforcerTestUtils.newPlugin("group", "a-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo", null));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo2", ""));

        List<String> additional = new ArrayList<>();
        additional.add("group:a-artifact");
        additional.add("group:another-artifact");

        Map<String, Plugin> results = rule.addAdditionalPlugins(plugins, additional);

        // make sure only one new plugin has been added
        assertNotNull(results);
        assertEquals(4, results.size());
        assertContainsPlugin("group", "a-artifact", results);
        assertContainsPlugin("group", "another-artifact", results);
    }

    /**
     * Test remove Unchecked plugins.
     *
     */
    @Test
    void testGetUncheckedPlugins() throws Exception {

        Map<String, Plugin> plugins = new LinkedHashMap<>();
        add(plugins, EnforcerTestUtils.newPlugin("group", "a-artifact", "1.0"));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo", null));
        add(plugins, EnforcerTestUtils.newPlugin("group", "foo2", ""));

        List<String> unchecked = new ArrayList<>();
        // intentionally inserting spaces to make sure they are handled correctly.
        unchecked.add("group : a-artifact");

        Map<String, Plugin> results = rule.removeUncheckedPlugins(unchecked, plugins);

        // make sure only one new plugin has been added
        assertNotNull(results);
        assertEquals(2, results.size());
        assertContainsPlugin("group", "foo", results);
        assertContainsPlugin("group", "foo2", results);
        assertNotContainPlugin("group", "a-artifact", results);
    }

    /**
     * Test combining values from both lists
     */
    @Test
    void testCombinePlugins() {

        Set<String> plugins = new HashSet<>();
        plugins.add("group:a-artifact");
        plugins.add("group:foo");
        plugins.add("group:foo2");

        Collection<String> results = rule.combineUncheckedPlugins(plugins, "group2:a,group3:b");

        // make sure only one new plugin has been added
        assertNotNull(results);
        assertEquals(5, results.size());
        assertTrue(results.contains("group:foo"));
        assertTrue(results.contains("group:foo2"));
        assertTrue(results.contains("group:a-artifact"));
        assertTrue(results.contains("group2:a"));
        assertTrue(results.contains("group3:b"));
    }

    /**
     * Test combining with an empty list
     */
    @Test
    void testCombinePlugins1() {

        Set<String> plugins = new HashSet<>();
        Collection<String> results = rule.combineUncheckedPlugins(plugins, "group2:a,group3:b");

        // make sure only one new plugin has been added
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.contains("group2:a"));
        assertTrue(results.contains("group3:b"));
    }

    /**
     * Test combining with a null list
     */
    @Test
    void testCombinePlugins2() {

        Collection<String> results = rule.combineUncheckedPlugins(null, "group2:a,group3:b");

        // make sure only one new plugin has been added
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.contains("group2:a"));
        assertTrue(results.contains("group3:b"));
    }

    /**
     * Test combining with an empty string
     */
    @Test
    void testCombinePlugins3() {

        Set<String> plugins = new HashSet<>();
        plugins.add("group:a-artifact");
        plugins.add("group:foo");
        plugins.add("group:foo2");

        Collection<String> results = rule.combineUncheckedPlugins(plugins, "");
        assertNotNull(results);
        assertEquals(3, results.size());
        assertTrue(results.contains("group:foo"));
        assertTrue(results.contains("group:foo2"));
        assertTrue(results.contains("group:a-artifact"));
    }

    /**
     * Test combining with a null string
     */
    @Test
    void testCombinePlugins4() {

        Set<String> plugins = new HashSet<>();
        plugins.add("group:a-artifact");
        plugins.add("group:foo");
        plugins.add("group:foo2");

        Collection<String> results = rule.combineUncheckedPlugins(plugins, null);
        assertNotNull(results);
        assertEquals(3, results.size());
        assertTrue(results.contains("group:foo"));
        assertTrue(results.contains("group:foo2"));
        assertTrue(results.contains("group:a-artifact"));
    }

    /**
     * Test combining with an invalid plugin string
     */
    @Test
    void testCombinePlugins5() {

        Set<String> plugins = new HashSet<>();
        plugins.add("group:a-artifact");
        plugins.add("group:foo");
        plugins.add("group:foo2");

        Collection<String> results = rule.combineUncheckedPlugins(plugins, "a");
        assertNotNull(results);
        assertEquals(4, results.size());
        assertTrue(results.contains("group:foo"));
        assertTrue(results.contains("group:foo2"));
        // this should be here, the checking of a valid plugin string happens in another method.
        assertTrue(results.contains("a"));
    }

    /**
     * Assert contains plugin.
     *
     * @param group    the group
     * @param artifact the artifact
     * @param plugins   the set
     */
    private void assertContainsPlugin(String group, String artifact, Map<String, Plugin> plugins) {
        assertTrue(plugins.containsKey(group + ":" + artifact));
    }

    /**
     * Assert doesn't contain plugin.
     *
     * @param group    the group
     * @param artifact the artifact
     * @param plugins   the set
     */
    private void assertNotContainPlugin(String group, String artifact, Map<String, Plugin> plugins) {
        assertFalse(plugins.containsKey(group + ":" + artifact));
    }

    private static void add(Map<String, Plugin> plugins, Plugin plugin) {
        plugins.put(plugin.getKey(), plugin);
    }

    private static void add(List<Plugin> plugins, Plugin plugin) {
        plugins.add(plugin);
    }

    /**
     * Test id.
     */
    @Test
    void testId() {
        rule.getCacheId();
    }
}
