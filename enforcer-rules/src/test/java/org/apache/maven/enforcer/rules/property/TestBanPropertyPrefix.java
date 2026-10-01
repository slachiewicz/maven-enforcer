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
package org.apache.maven.enforcer.rules.property;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Model;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link BanPropertyPrefix}.
 */
@ExtendWith(MockitoExtension.class)
class TestBanPropertyPrefix {

    private static final String BANNED_PREFIX = "secured.system";

    private static final String OTHER_PREFIX = "other.prefix";

    @Mock
    private Project project;

    @InjectMocks
    private BanPropertyPrefix rule;

    private Map<String, String> properties;

    @Test
    void shouldFailWhenPropertyWithBannedPrefixExists() {
        properties.put(BANNED_PREFIX + ".timeout", "5000");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertThat(e.getMessage()).contains(BANNED_PREFIX + ".timeout").contains(BANNED_PREFIX);
    }

    @Test
    void shouldPassWhenNoPropertyWithBannedPrefixExists() {
        properties.put(OTHER_PREFIX + ".value", "x");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX));

        assertDoesNotThrow(rule::execute);
    }

    @Test
    void shouldPassWhenProjectHasNoProperties() {
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX));

        assertDoesNotThrow(rule::execute);
    }

    @Test
    void shouldMatchAnyConfiguredPrefix() {
        properties.put(OTHER_PREFIX + ".value", "x");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Arrays.asList(BANNED_PREFIX, OTHER_PREFIX));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertThat(e.getMessage()).contains(OTHER_PREFIX + ".value");
    }

    @Test
    void shouldListAllOffendingPropertiesSorted() {
        properties.put(BANNED_PREFIX + ".two", "2");
        properties.put("keep.me", "ok");
        properties.put(BANNED_PREFIX + ".one", "1");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertThat(e.getMessage()).contains(BANNED_PREFIX + ".one", BANNED_PREFIX + ".two");
        assertThat(e.getMessage()).containsSubsequence(BANNED_PREFIX + ".one", BANNED_PREFIX + ".two");
        assertThat(e.getMessage()).doesNotContain("keep.me");
        assertThat(e.getMessage()).doesNotContain("=1", "=2");
    }

    @Test
    void shouldSupportCommaSeparatedEntry() {
        properties.put(BANNED_PREFIX + ".timeout", "5000");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX + "," + OTHER_PREFIX));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertThat(e.getMessage()).contains(BANNED_PREFIX + ".timeout");
    }

    @Test
    void shouldIgnoreBlankAndDuplicatePrefixes() {
        properties.put(OTHER_PREFIX + ".value", "x");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Arrays.asList(" " + BANNED_PREFIX + " ", " ", " " + BANNED_PREFIX));

        assertDoesNotThrow(rule::execute);
    }

    @Test
    void shouldUseCustomMessageWhenConfigured() {
        properties.put(BANNED_PREFIX + ".timeout", "5000");
        when(project.getModel())
                .thenReturn(Model.newBuilder().properties(properties).build());
        rule.setPrefixes(Collections.singletonList(BANNED_PREFIX));
        rule.setMessage("Custom failure message");

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertThat(e.getMessage()).isEqualTo("Custom failure message");
    }

    @Test
    void shouldPassWhenNoPrefixesConfigured() {
        assertDoesNotThrow(rule::execute);
    }

    @Test
    void ruleShouldNotBeCached() {
        assertThat(rule.getCacheId()).isNull();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        properties = new HashMap<>();
        rule = new BanPropertyPrefix(project);
    }
}
