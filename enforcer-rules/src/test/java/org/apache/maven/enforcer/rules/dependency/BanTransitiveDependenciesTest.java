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
import java.util.List;

import org.apache.maven.api.Node;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BanTransitiveDependenciesTest {

    private ResolverUtil resolverUtil;

    private BanTransitiveDependencies rule;

    @BeforeEach
    void setUp() throws Exception {
        resolverUtil = mock(ResolverUtil.class);
        rule = new BanTransitiveDependencies(resolverUtil, EnforcerTestUtils.getVersionParser());
        rule.setLog(mock(EnforcerLogger.class));
    }

    private void setExcludes(List<String> excludes) throws ReflectiveOperationException {
        Field field = BanTransitiveDependencies.class.getDeclaredField("excludes");
        field.setAccessible(true);
        field.set(rule, excludes);
    }

    private static DependencyNodeBuilder node(String artifactId) {
        return new DependencyNodeBuilder().withArtifactId(artifactId).withVersion("1.0");
    }

    @Test
    void shouldPassWithDirectDependenciesOnly() throws Exception {
        Node root = new DependencyNodeBuilder()
                .withChildNode(node("a").build())
                .withChildNode(node("b").build())
                .build();
        when(resolverUtil.resolveTransitiveDependencies()).thenReturn(root);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWithTransitiveDependency() throws Exception {
        Node root = new DependencyNodeBuilder()
                .withChildNode(node("a").withChildNode(node("c").build()).build())
                .build();
        when(resolverUtil.resolveTransitiveDependencies()).thenReturn(root);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("default-group:a:jar:classifier:1.0 has transitive dependencies")
                .hasMessageContaining("default-group:c:jar:classifier:1.0");
    }

    @Test
    void shouldPassWhenTransitiveDependencyIsExcluded() throws Exception {
        Node root = new DependencyNodeBuilder()
                .withChildNode(node("a").withChildNode(node("c").build()).build())
                .build();
        when(resolverUtil.resolveTransitiveDependencies()).thenReturn(root);
        setExcludes(Collections.singletonList("default-group:a"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }
}
