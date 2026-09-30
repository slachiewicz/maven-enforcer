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

import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestRequireReleaseVersion {

    @Mock
    private Project project;

    @Mock
    private ProducedArtifact artifact;

    private RequireReleaseVersion rule;

    @BeforeEach
    void setUp() {
        rule = new RequireReleaseVersion(project, EnforcerTestUtils.getVersionParser());
    }

    private static Model modelWithParent(String parentVersion) {
        return Model.newBuilder()
                .parent(Parent.newBuilder()
                        .groupId("testGroupId")
                        .artifactId("parent")
                        .version(parentVersion)
                        .build())
                .build();
    }

    @Test
    void testProjectWithReleaseVersion() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(false);
        when(project.getModel()).thenReturn(Model.newInstance());

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void testProjectWithSnapshotVersion() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);
        when(project.getId()).thenReturn("testGroupId:snapshot:jar:2.0-SNAPSHOT");

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("This project cannot be a snapshot");
    }

    @Test
    void shouldFailWhenParentIsSnapshot() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(false);
        when(project.getModel()).thenReturn(modelWithParent("2.0-SNAPSHOT"));

        rule.setFailWhenParentIsSnapshot(true);

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("Parent Cannot be a snapshot");
    }

    @Test
    void shouldAllowParentSnapshot() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(false);

        rule.setFailWhenParentIsSnapshot(false);

        assertThatCode(rule::execute).doesNotThrowAnyException();

        verify(project, never()).getModel();
    }

    @Test
    void testCache() {
        assertThat(rule.getCacheId()).isNull();
    }
}
