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

import java.util.Collections;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Prerequisites;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirePrerequisiteTest {

    @Mock
    private Project project;

    @Mock
    private EnforcerLogger log;

    private RequirePrerequisite rule;

    private String packaging = "maven-plugin";

    private Prerequisites prerequisites;

    @BeforeEach
    void before() {
        rule = new RequirePrerequisite(project, EnforcerTestUtils.getVersionParser());
        rule.setLog(log);
        // the model is created when the project is asked for it, after the test is set up
        when(project.getModel())
                .thenAnswer(i -> Model.newBuilder()
                        .packaging(packaging)
                        .prerequisites(prerequisites)
                        .build());
    }

    @Test
    void testNoPrerequisite() {
        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    @Test
    void shouldOutputCustomMessageWhenPrerequisiteNotSet() {
        String customMessage = "Custom prerequisite message";
        rule.setMessage(customMessage);

        assertThatThrownBy(() -> rule.execute())
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageStartingWith(customMessage)
                .hasMessageContaining("Requires prerequisite not set");
    }

    @Test
    void testNoSpecifiedPrerequisite() throws Exception {
        prerequisites = Prerequisites.newInstance();
        rule.execute();
    }

    @Test
    void testLowerMavenPrerequisite() {
        prerequisites = Prerequisites.newInstance();

        assertThrows(EnforcerRuleException.class, () -> {
            rule.setMavenVersion("3.0");
            rule.execute();
        });
    }

    @Test
    void testLowerMavenRangePrerequisite() {
        prerequisites = Prerequisites.newInstance();

        assertThrows(EnforcerRuleException.class, () -> {
            rule.setMavenVersion("[3.0,)");
            rule.execute();
        });
    }

    @Test
    void testMavenRangesPrerequisite() {
        assertThrows(EnforcerRuleException.class, () -> {
            prerequisites = Prerequisites.newBuilder().maven("2.2.0").build();

            rule.setMavenVersion("[2.0.6,2.1.0),(2.1.0,2.2.0),(2.2.0,)");
            rule.execute();
        });
    }

    @Test
    void testValidPrerequisite() throws Exception {
        prerequisites = Prerequisites.newBuilder().maven("3.0").build();

        rule.setMavenVersion("2.2.1");
        rule.execute();
    }

    @Test
    void testPomPackaging() throws Exception {
        packaging = "pom";

        rule.execute();

        verify(log).debug("Packaging is pom, skipping requirePrerequisite rule");
    }

    @Test
    void testMatchingPackagings() {
        assertThrows(EnforcerRuleException.class, () -> {
            packaging = "maven-plugin";

            rule.setPackagings(Collections.singletonList("maven-plugin"));
            rule.execute();
        });
    }

    @Test
    void testNotMatchingPackagings() throws Exception {
        packaging = "jar";

        rule.setPackagings(Collections.singletonList("maven-plugin"));
        rule.execute();

        verify(log).debug("Packaging is jar, skipping requirePrerequisite rule");
    }
}
