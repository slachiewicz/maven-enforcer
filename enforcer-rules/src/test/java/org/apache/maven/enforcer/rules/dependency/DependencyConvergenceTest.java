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
package org.apache.maven.enforcer.rules.dependency;

import java.lang.reflect.Field;
import java.util.Collections;

import org.apache.maven.api.Node;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyConvergenceTest {

    @Mock
    private ResolverUtil resolverUtil;

    private DependencyConvergence rule;

    @BeforeEach
    void setUp() {
        rule = new DependencyConvergence(resolverUtil, EnforcerTestUtils.getVersionParser());
        rule.setLog(mock(EnforcerLogger.class));
    }

    private void setField(String fieldName, Object value) throws ReflectiveOperationException {
        Field field = DependencyConvergence.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(rule, value);
    }

    private static DependencyNodeBuilder node(String artifactId, String version) {
        return new DependencyNodeBuilder().withArtifactId(artifactId).withVersion(version);
    }

    /**
     * The root has a and b, both with a different version of c.
     */
    private static Node conflictingTree(String versionOfCInB) {
        return new DependencyNodeBuilder()
                .withChildNode(
                        node("a", "1.0").withChildNode(node("c", "1.0").build()).build())
                .withChildNode(node("b", "1.0")
                        .withChildNode(node("c", versionOfCInB).build())
                        .build())
                .build();
    }

    @Test
    void shouldPassWhenVersionsAreTheSame() throws Exception {
        when(resolverUtil.resolveTransitiveDependenciesVerbose(anyList())).thenReturn(conflictingTree("1.0"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWithPathsWhenVersionsDiffer() throws Exception {
        when(resolverUtil.resolveTransitiveDependenciesVerbose(anyList())).thenReturn(conflictingTree("2.0"));

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("Failed while enforcing releasability.")
                .hasMessageContaining(
                        "Dependency convergence error for default-group:c:jar:classifier:1.0. Paths to dependency are:")
                .hasMessageContaining("+-default-group:default-artifact:jar:classifier:default-version:compile")
                .hasMessageContaining("  +-default-group:a:jar:classifier:1.0:compile")
                .hasMessageContaining("    +-default-group:c:jar:classifier:1.0:compile")
                .hasMessageContaining("and")
                .hasMessageContaining("  +-default-group:b:jar:classifier:1.0:compile")
                .hasMessageContaining("    +-default-group:c:jar:classifier:2.0:compile");
    }

    @Test
    void shouldIgnoreExcludedArtifact() throws Exception {
        when(resolverUtil.resolveTransitiveDependenciesVerbose(anyList())).thenReturn(conflictingTree("2.0"));
        setField("excludes", Collections.singletonList("default-group:c"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldCheckOnlyIncludedArtifact() throws Exception {
        when(resolverUtil.resolveTransitiveDependenciesVerbose(anyList())).thenReturn(conflictingTree("2.0"));
        setField("includes", Collections.singletonList("default-group:a"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenSnapshotVersionsAreTheSame() throws Exception {
        Node tree = new DependencyNodeBuilder()
                .withChildNode(node("a", "1.0")
                        .withChildNode(node("c", "1.0-SNAPSHOT").build())
                        .build())
                .withChildNode(node("b", "1.0")
                        .withChildNode(node("c", "1.0-SNAPSHOT").build())
                        .build())
                .build();
        when(resolverUtil.resolveTransitiveDependenciesVerbose(anyList())).thenReturn(tree);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }
}
