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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestRequireSnapshotVersion {

    @Mock
    private Project project;

    @Mock
    private ProducedArtifact artifact;

    private RequireSnapshotVersion rule;

    @BeforeEach
    public void before() {
        rule = new RequireSnapshotVersion(project, EnforcerTestUtils.getVersionParser());
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
    void shouldFailForRelease() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(false);
        when(project.getId()).thenReturn("testGroupId:release:jar:1.0");

        assertThatCode(rule::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void shouldPassForSnapshot() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);
        when(project.getModel()).thenReturn(Model.newInstance());

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldFailForReleaseParent() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);
        when(project.getModel()).thenReturn(modelWithParent("1.0"));

        rule.setFailWhenParentIsRelease(true);

        assertThatCode(rule::execute).isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void shouldPassForSnapshotParent() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);
        when(project.getModel()).thenReturn(modelWithParent("2.0-SNAPSHOT"));

        rule.setFailWhenParentIsRelease(true);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void parentShouldNotBeChecked() throws Exception {
        when(project.getPomArtifact()).thenReturn(artifact);
        when(artifact.isSnapshot()).thenReturn(true);

        rule.setFailWhenParentIsRelease(false);

        assertThatCode(rule::execute).doesNotThrowAnyException();

        verify(project).getPomArtifact();
        verifyNoMoreInteractions(project);
    }
}
