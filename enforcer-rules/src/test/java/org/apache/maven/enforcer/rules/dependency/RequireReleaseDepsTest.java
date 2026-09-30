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

import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.apache.maven.enforcer.rules.utils.EnforcerArtifactStubFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.enforcer.rules.EnforcerTestUtils.getDependencyNodeWithMultipleSnapshots;
import static org.apache.maven.enforcer.rules.EnforcerTestUtils.getDependencyNodeWithMultipleTestSnapshots;
import static org.apache.maven.enforcer.rules.EnforcerTestUtils.getDependencyNodeWithOptionalSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequireReleaseDeps}
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>, Andrzej Jarmoniuk
 */
@ExtendWith(MockitoExtension.class)
class RequireReleaseDepsTest {
    private static final EnforcerArtifactStubFactory ARTIFACT_STUB_FACTORY = new EnforcerArtifactStubFactory();

    @Mock
    private Project project;

    @Mock
    private ResolverUtil resolverUtil;

    private RequireReleaseDeps rule;

    @BeforeEach
    void setUp() {
        rule = new RequireReleaseDeps(project, resolverUtil, EnforcerTestUtils.getVersionParser());
        // a project without a parent, unless the test says otherwise
        lenient().when(project.getModel()).thenReturn(Model.newInstance());
    }

    private static Model modelWithSnapshotParent() {
        return Model.newBuilder()
                .parent(Parent.newBuilder()
                        .groupId("testGroupId")
                        .artifactId("snapshot")
                        .version("2.0-SNAPSHOT")
                        .build())
                .build();
    }

    @Test
    void testSearchNonTransitive() throws Exception {
        // the direct dependencies are the children of the root node
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(DependencyNodeBuilder.rootOf(ARTIFACT_STUB_FACTORY.getScopedArtifacts()));
        rule.setSearchTransitive(false);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void testSearchTransitiveMultipleFailures() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleSnapshots());
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining(
                        "default-group:childAA:jar:classifier:1.0.0-SNAPSHOT <--- is not a release dependency")
                .hasMessageContaining(
                        "default-group:childB:jar:classifier:2.0.0-SNAPSHOT <--- is not a release dependency");
    }

    @Test
    void testSearchTransitiveNoFailures() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new DependencyNodeBuilder().build());

        rule.setSearchTransitive(true);
        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void testShouldFailOnlyWhenRelease() throws Exception {
        ProducedArtifact artifact = mock(ProducedArtifact.class);
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);
        rule.setOnlyWhenRelease(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();

        verifyNoInteractions(resolverUtil);
    }

    @Test
    void testWildcardExcludeTests() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleTestSnapshots());

        rule.setExcludes(Collections.singletonList("*:*:*:*:test"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void testWildcardExcludeAll() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleTestSnapshots());

        rule.setExcludes(Collections.singletonList("*"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void testExcludesAndIncludes() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleTestSnapshots());

        rule.setExcludes(Collections.singletonList("*"));
        rule.setIncludes(Collections.singletonList("*:*:*:*:test"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining(
                        "default-group:childAA:jar:classifier:1.0.0-SNAPSHOT <--- is not a release dependency")
                .hasMessageContaining(
                        "default-group:childB:jar:classifier:2.0.0-SNAPSHOT <--- is not a release dependency");
    }

    @Test
    void snapshotsExcludedByCoordinatesShouldPass() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleSnapshots());

        rule.setExcludes(Arrays.asList("default-group:childAA", "default-group:childB"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void excludeWithNonMatchingVersionRangeShouldStillFail() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithMultipleSnapshots());

        rule.setMessage("No Snapshots Allowed!");
        rule.setExcludes(Arrays.asList("default-group:childAA:[2.0,)", "default-group:childB"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("No Snapshots Allowed!")
                .hasMessageContaining(
                        "default-group:childAA:jar:classifier:1.0.0-SNAPSHOT <--- is not a release dependency")
                .hasMessageNotContaining("childB");
    }

    @Test
    void includesWithoutAnyDependencyShouldPass() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new DependencyNodeBuilder().build());

        rule.setIncludes(Collections.singletonList("testgroup:testartifact"));
        rule.setSearchTransitive(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    /**
     * Test id.
     */
    @Test
    void testId() {
        assertThat(rule.getCacheId()).isNull();
    }

    @Test
    void testFailWhenParentIsSnapshot() throws Exception {
        when(project.getModel()).thenReturn(modelWithSnapshotParent());
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new DependencyNodeBuilder().build());

        rule.setFailWhenParentIsSnapshot(true);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("Parent Cannot be a snapshot: testGroupId:snapshot:pom:2.0-SNAPSHOT");
    }

    @Test
    void parentShouldBeExcluded() throws Exception {
        when(project.getModel()).thenReturn(modelWithSnapshotParent());
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new DependencyNodeBuilder().build());

        rule.setFailWhenParentIsSnapshot(true);
        rule.setExcludes(Collections.singletonList("testGroupId:*"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void optionalSnapshotDependencyShouldFail() throws Exception {
        when(resolverUtil.resolveTransitiveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(getDependencyNodeWithOptionalSnapshot());

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining(
                        "default-group:optional-childA-snapshot:jar:classifier:1.0.0-SNAPSHOT <--- is not a release dependency")
                .hasMessageContaining(
                        "default-group:childB:jar:classifier:2.0.0-SNAPSHOT <--- is not a release dependency");
    }
}
