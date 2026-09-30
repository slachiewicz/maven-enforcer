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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Model;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Check reactorModuleConvergence rule.
 *
 * @author <a href="mailto:khmarbaise@apache.org">Karl Heinz Marbaise</a>
 */
class ReactorModuleConvergenceTest {
    private Session session;

    private ReactorModuleConvergence rule;

    @BeforeEach
    public void before() {
        session = mock(Session.class);
        rule = new ReactorModuleConvergence(session);
        rule.setLog(mock(EnforcerLogger.class));
    }

    private void setupSortedProjects(List<Project> projectList) {
        when(session.getProjects()).thenReturn(projectList);
    }

    @Test
    void shouldNotFailWithNoProject() throws EnforcerRuleException {
        setupSortedProjects(Collections.emptyList());

        rule.execute();
    }

    @Test
    void shouldNotFailWithAValidProject() throws EnforcerRuleException {
        Project mp1 = createProjectParent();
        Project mp2 = createProjectChild1(mp1);
        Project mp3 = createProjectChild2(mp1);

        List<Project> projects = Arrays.asList(mp1, mp2, mp3);
        setupSortedProjects(projects);

        rule.execute();
    }

    @Test
    void shouldFailWithWrongVersionInOneChild() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project mp1 = createProjectParent();
            Project mp2 = createProjectChild1(mp1);
            Project mp3 = createProjectChild2WithWrongVersion(mp1);

            List<Project> projects = Arrays.asList(mp1, mp2, mp3);
            setupSortedProjects(projects);

            rule.execute();

            // intentionally no assertTrue() cause we expect getting an exception.
        });

        // intentionally no assertTrue() cause we expect getting an exception.
    }

    @Test
    void shouldFailWithWrongParent() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project mp1 = createProjectParent();

            Project wrongParentVersion = mock(Project.class);
            when(wrongParentVersion.getGroupId()).thenReturn("org.apache.enforcer");
            when(wrongParentVersion.getArtifactId()).thenReturn("m1");
            when(wrongParentVersion.getVersion()).thenReturn("1.1-SNAPSHOT");
            when(wrongParentVersion.getId()).thenReturn("org.apache.enforcer:m1:jar:1.1-SNAPSHOT");
            when(wrongParentVersion.getModel())
                    .thenReturn(Model.newBuilder()
                            .dependencies(Collections.emptyList())
                            .build());

            Project mp2 = createProjectChild2(wrongParentVersion);
            Project mp3 = createProjectChild2(mp1);

            List<Project> projects = Arrays.asList(mp1, mp2, mp3);
            setupSortedProjects(projects);

            rule.execute();

            // intentionally no assertTrue() cause we expect getting an exception.
        });

        // intentionally no assertTrue() cause we expect getting an exception.
    }

    @Test
    void shouldNotFailWithACompanyParent() throws EnforcerRuleException {
        Project companyParent = createCompanyParent();
        Project mp1 = createProjectParent(companyParent);

        Project mp2 = createProjectChild1(mp1);
        Project mp3 = createProjectChild2(mp1);

        List<Project> projects = Arrays.asList(mp1, mp2, mp3);
        setupSortedProjects(projects);

        rule.execute();
    }

    @Test
    void shouldFailWithMissingParentsInReactory() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project mp1 = createProjectParent();
            Project mp2 = createProjectChild1(mp1);
            Project mp3 = createProjectChild2(null);

            List<Project> projects = Arrays.asList(mp1, mp2, mp3);
            setupSortedProjects(projects);

            rule.execute();
        });
    }

    @Test
    void shouldFailWithAParentWhichIsNotPartOfTheReactory() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project mp1 = createProjectParent();

            Project wrongParentVersion = mock(Project.class);
            when(wrongParentVersion.getGroupId()).thenReturn("org.apache");
            when(wrongParentVersion.getArtifactId()).thenReturn("m1");
            when(wrongParentVersion.getVersion()).thenReturn("1.0-SNAPSHOT");
            when(wrongParentVersion.getId()).thenReturn("org.apache.enforcer:m1:jar:1.0-SNAPSHOT");
            when(wrongParentVersion.getModel())
                    .thenReturn(Model.newBuilder()
                            .dependencies(Collections.emptyList())
                            .build());

            Project mp2 = createProjectChild2(wrongParentVersion);
            Project mp3 = createProjectChild2(mp1);

            List<Project> projects = Arrays.asList(mp1, mp2, mp3);
            setupSortedProjects(projects);

            rule.execute();

            // intentionally no assertTrue() cause we expect getting an exception.
        });

        // intentionally no assertTrue() cause we expect getting an exception.
    }

    @Test
    void shouldNotFailWithDependencyInReactory() throws EnforcerRuleException {
        Project mp1 = createProjectParent();
        Project mp2 = createProjectChild1(mp1);

        Dependency goodDependency = createDependency("org.junit", "junit", "2.0");
        List<Dependency> depListMP2 = Arrays.asList(goodDependency);
        when(mp2.getModel())
                .thenReturn(Model.newBuilder().dependencies(depListMP2).build());

        Project mp3 = createProjectChild2(mp1);
        Dependency dep1Mp3 = createDependency("org.apache.commons", "commons-io", "1.0.4");
        List<Dependency> depListMP3 = Arrays.asList(dep1Mp3);
        when(mp3.getModel())
                .thenReturn(Model.newBuilder().dependencies(depListMP3).build());

        List<Project> projects = Arrays.asList(mp1, mp2, mp3);
        setupSortedProjects(projects);

        rule.execute();
    }

    @Test
    void shouldFailWithWrongDependencyInReactor() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project mp1 = createProjectParent();
            Project mp2 = createProjectChild1(mp1);

            Dependency goodDependency = createDependency("org.junit", "junit", "2.0");

            Dependency wrongDepFromReactor = createDependency("org.apache.enforcer", "m2", "1.1-SNAPSHOT");
            List<Dependency> depList = Arrays.asList(goodDependency, wrongDepFromReactor);
            when(mp2.getModel())
                    .thenReturn(Model.newBuilder().dependencies(depList).build());

            Project mp3 = createProjectChild2(mp1);

            List<Project> projects = Arrays.asList(mp1, mp2, mp3);
            setupSortedProjects(projects);

            rule.execute();

            // intentionally no assertTrue() cause we expect getting an exception.
        });

        // intentionally no assertTrue() cause we expect getting an exception.
    }

    @Test
    void cacheIdShouldBeSet() {
        assertThat(rule.getCacheId()).isNotEmpty();
    }

    /**
     * This small setup is equivalent to the following situation:
     *
     * <pre>
     *  &lt;parent&gt;
     *    &lt;groupId&gt;...&lt;/groupId&gt;
     *    &lt;artifactId&gt;...&lt;/artifactId&gt;
     *    &lt;version&gt;1.0-SNAPSHOT&lt;/version&gt;
     *  &lt;/parent&gt;
     *
     *  &lt;version&gt;1.1-SNAPSHOT&lt;/version&gt;
     * </pre>
     *
     * @param parent
     * @return Create Project mock.
     */
    private Project createProjectChild2WithWrongVersion(Project parent) {
        Project mp2 = mock(Project.class);
        when(mp2.getParent()).thenReturn(Optional.ofNullable(parent));
        when(mp2.getGroupId()).thenReturn("org.apache.enforcer");
        when(mp2.getArtifactId()).thenReturn("m1");
        when(mp2.getVersion()).thenReturn("1.1-SNAPSHOT");
        when(mp2.getId()).thenReturn("org.apache.enforcer:m1:jar:1.1-SNAPSHOT");
        when(mp2.getModel())
                .thenReturn(
                        Model.newBuilder().dependencies(Collections.emptyList()).build());
        return mp2;
    }

    private Project createProjectChild2(Project parent) {
        Project mp3 = mock(Project.class);
        when(mp3.getParent()).thenReturn(Optional.ofNullable(parent));
        when(mp3.getGroupId()).thenReturn("org.apache.enforcer");
        when(mp3.getArtifactId()).thenReturn("m2");
        when(mp3.getVersion()).thenReturn("1.0-SNAPSHOT");
        when(mp3.getId()).thenReturn("org.apache.enforcer:m2:jar:1.0-SNAPSHOT");
        when(mp3.getModel())
                .thenReturn(
                        Model.newBuilder().dependencies(Collections.emptyList()).build());
        return mp3;
    }

    private Project createProjectChild1(Project parent) {
        Project mp2 = mock(Project.class);
        when(mp2.getParent()).thenReturn(Optional.ofNullable(parent));
        when(mp2.getGroupId()).thenReturn("org.apache.enforcer");
        when(mp2.getArtifactId()).thenReturn("m1");
        when(mp2.getVersion()).thenReturn("1.0-SNAPSHOT");
        when(mp2.getId()).thenReturn("org.apache.enforcer:m1:jar:1.0-SNAPSHOT");
        when(mp2.getModel())
                .thenReturn(
                        Model.newBuilder().dependencies(Collections.emptyList()).build());
        return mp2;
    }

    private Project createCompanyParent() {
        Project nonReactorParent = mock(Project.class);
        when(nonReactorParent.getGroupId()).thenReturn("org.apache.enforcer.parent");
        when(nonReactorParent.getArtifactId()).thenReturn("parent");
        when(nonReactorParent.getVersion()).thenReturn("1.1");
        when(nonReactorParent.getId()).thenReturn("org.apache.enforcer.parent:parent:jar:1.1");
        when(nonReactorParent.getModel())
                .thenReturn(
                        Model.newBuilder().dependencies(Collections.emptyList()).build());
        return nonReactorParent;
    }

    private Project createProjectParent(Project nonReactorParent) {
        Project m = createProjectParent();
        when(m.isTopProject()).thenReturn(true);
        when(m.getParent()).thenReturn(Optional.ofNullable(nonReactorParent));
        return m;
    }

    private Project createProjectParent() {
        Project mp1 = mock(Project.class);
        when(mp1.isTopProject()).thenReturn(true);
        when(mp1.getParent()).thenReturn(Optional.ofNullable(null));
        when(mp1.getGroupId()).thenReturn("org.apache.enforcer");
        when(mp1.getArtifactId()).thenReturn("parent");
        when(mp1.getVersion()).thenReturn("1.0-SNAPSHOT");
        when(mp1.getId()).thenReturn("org.apache.enforcer:parent:pom:1.0-SNAPSHOT");
        when(mp1.getModel())
                .thenReturn(
                        Model.newBuilder().dependencies(Collections.emptyList()).build());
        return mp1;
    }

    private Dependency createDependency(String groupId, String artifactId, String version) {
        return Dependency.newBuilder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .build();
    }
}
