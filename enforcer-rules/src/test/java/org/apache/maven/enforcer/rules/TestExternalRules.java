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

import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.Plugin;
import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestExternalRules {

    @Mock
    private MojoExecution mojoExecution;

    @Mock
    private ExpressionEvaluator evaluator;

    @Mock
    private EnforcerLogger logger;

    @InjectMocks
    private ExternalRules rule;

    @BeforeEach
    void setup() {
        rule.setLog(logger);
    }

    @Test
    void shouldFailIfNoLocationIsSet() {
        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> rule.getRulesConfig())
                .withMessage("No location provided");
    }

    @Test
    void shouldFailIfClasspathLocationIsNotFound() {

        Plugin plugin = mock(Plugin.class);
        when(plugin.getClassLoader()).thenReturn(EnforcerTestUtils.class.getClassLoader());
        when(mojoExecution.getPlugin()).thenReturn(plugin);
        rule.setLocation("classpath:foo");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> rule.getRulesConfig())
                .withMessage("Location 'foo' not found in classpath");
    }

    @Test
    void shouldFailIfFileLocationIsNotFound() {
        when(evaluator.alignToBaseDirectory(any())).thenAnswer(i -> i.getArgument(0));

        rule.setLocation("blah.xml");
        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> rule.getRulesConfig())
                .withMessageMatching("Could not read descriptor in .*blah.xml");
    }

    @Test
    void shouldLoadRulesFromClassPath() throws EnforcerRuleException {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getClassLoader()).thenReturn(EnforcerTestUtils.class.getClassLoader());
        when(mojoExecution.getPlugin()).thenReturn(plugin);
        rule.setLocation("classpath:enforcer-rules/pass.xml");

        XmlNode rulesConfig = rule.getRulesConfig();
        assertNotNull(rulesConfig);
        assertEquals(2, rulesConfig.children().size());
    }

    @Test
    void shouldFilterRules() throws EnforcerRuleException {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getClassLoader()).thenReturn(EnforcerTestUtils.class.getClassLoader());
        when(mojoExecution.getPlugin()).thenReturn(plugin);
        rule.setLocation("classpath:enforcer-rules/banned-dependencies.xml");
        rule.setXsltLocation("classpath:enforcer-rules/allow-findbugs.xsl");

        XmlNode rulesConfig = rule.getRulesConfig();
        assertNotNull(rulesConfig);
        assertEquals(1, rulesConfig.children().size());
        assertEquals("bannedDependencies", rulesConfig.children().get(0).name());
        assertEquals(1, rulesConfig.children().get(0).children().size());
        assertEquals("excludes", rulesConfig.children().get(0).children().get(0).name());
        assertEquals(
                1, rulesConfig.children().get(0).children().get(0).children().size());
        assertEquals(
                "exclude",
                rulesConfig
                        .children()
                        .get(0)
                        .children()
                        .get(0)
                        .children()
                        .get(0)
                        .name());
        assertEquals(
                "com.google.guava:listenablefuture",
                rulesConfig
                        .children()
                        .get(0)
                        .children()
                        .get(0)
                        .children()
                        .get(0)
                        .value());
    }
}
