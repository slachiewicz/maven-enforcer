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
package org.apache.maven.enforcer.rules.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.settings.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the expressions supported by the evaluator.
 */
class TestExpressionEvaluator {

    private Session session;

    private Project project;

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        project = mock(Project.class);
        Path basedir = Paths.get("target", "base").toAbsolutePath();
        when(project.getBasedir()).thenReturn(basedir);
        when(project.getVersion()).thenReturn("1.2.3");
        when(project.getArtifactId()).thenReturn("my-artifact");

        Map<String, String> properties = new HashMap<>();
        properties.put("my.property", "my value");
        when(session.getEffectiveProperties(project)).thenReturn(properties);
        when(session.getSettings())
                .thenReturn(Settings.newBuilder().localRepository("repo").build());

        evaluator = new ExpressionEvaluator(session, project);
    }

    @Test
    void shouldEvaluateProjectValues() {
        assertThat(evaluator.evaluate("${project.version}")).isEqualTo("1.2.3");
        assertThat(evaluator.evaluate("${pom.artifactId}")).isEqualTo("my-artifact");
        assertThat(evaluator.evaluate("${project.version}-${project.artifactId}"))
                .isEqualTo("1.2.3-my-artifact");
    }

    @Test
    void shouldEvaluateProperties() {
        assertThat(evaluator.evaluate("${my.property}")).isEqualTo("my value");
    }

    @Test
    void shouldEvaluateSettings() {
        assertThat(evaluator.evaluate("${settings.localRepository}")).isEqualTo("repo");
    }

    @Test
    void shouldEvaluateEnvironmentVariables() {
        String path = System.getenv("PATH");
        assertThat(evaluator.evaluate("${env.PATH}")).isEqualTo(path);
        assertThat(evaluator.evaluate("${env.NOT_EXISTING_VARIABLE_XYZ}")).isNull();
    }

    @Test
    void shouldEvaluateBasedir() {
        assertThat(evaluator.evaluate("${basedir}"))
                .isEqualTo(project.getBasedir().toString());
    }

    @Test
    void shouldReturnNullForUnknownSingleExpression() {
        assertThat(evaluator.evaluate("${unknown}")).isNull();
        assertThat(evaluator.evaluate("${project.unknown}")).isNull();
    }

    @Test
    void shouldKeepUnknownExpressionInText() {
        assertThat(evaluator.evaluate("a ${unknown} b ${project.version}")).isEqualTo("a ${unknown} b 1.2.3");
    }

    @Test
    void shouldReturnTextWithoutExpression() {
        assertThat(evaluator.evaluate("text")).isEqualTo("text");
        assertThat(evaluator.evaluate(null)).isNull();
    }

    @Test
    void shouldAlignToBaseDirectory() {
        assertThat(evaluator.alignToBaseDirectory(new File("pom.xml")))
                .isEqualTo(new File(project.getBasedir().toFile(), "pom.xml"));

        File absolute = new File("target").getAbsoluteFile();
        assertThat(evaluator.alignToBaseDirectory(absolute)).isEqualTo(absolute);
    }
}
