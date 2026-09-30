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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.model.RepositoryPolicy;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRequireNoRepositories {

    private RequireNoRepositories rule;

    private Session session;

    // the original models are read again from the pom files, so they are kept by the path of pom file
    private final Map<Path, List<Repository>> repositories = new HashMap<>();

    private final Map<Path, List<Repository>> pluginRepositories = new HashMap<>();

    @BeforeEach
    void before() {
        session = mock(Session.class);

        ModelXmlFactory modelXmlFactory = mock(ModelXmlFactory.class);
        when(modelXmlFactory.read(any(XmlReaderRequest.class))).thenAnswer(invocation -> {
            Path path = invocation.<XmlReaderRequest>getArgument(0).getPath();
            return Model.newBuilder()
                    .groupId("org.apache.maven.plugins.enforcer.test")
                    .artifactId("no-repositories")
                    .version("1.0-SNAPSHOT")
                    .repositories(repositories.getOrDefault(path, Collections.emptyList()))
                    .pluginRepositories(pluginRepositories.getOrDefault(path, Collections.emptyList()))
                    .build();
        });

        rule = new RequireNoRepositories(session, modelXmlFactory);
        rule.setMessage("my message");
        rule.setLog(mock(EnforcerLogger.class));
    }

    private Project createMavenProject() {
        Project mp = mock(Project.class);
        when(mp.getGroupId()).thenReturn("org.apache.maven.plugins.enforcer.test");
        when(mp.getArtifactId()).thenReturn("no-repositories-child");
        when(mp.getVersion()).thenReturn("1.0-SNAPSHOT");
        when(mp.getPomPath())
                .thenReturn(Paths.get("project-" + repositories.size() + "-" + System.nanoTime() + "/pom.xml"));

        return mp;
    }

    private Project createStandAloneProject() {
        Project mp = createMavenProject();
        repositories.put(mp.getPomPath(), new ArrayList<>());
        pluginRepositories.put(mp.getPomPath(), new ArrayList<>());
        return mp;
    }

    private void setupSortedProjects(List<Project> projectList) {
        when(session.getProjects()).thenReturn(projectList);
        when(session.getSettings()).thenReturn(mock(Settings.class));
    }

    private Repository createRepository(String id, String url) {
        return Repository.newBuilder()
                .id(id)
                .url(url)
                .snapshots(RepositoryPolicy.newBuilder()
                        .enabled("false")
                        .updatePolicy("daily")
                        .build())
                .releases(RepositoryPolicy.newBuilder()
                        .enabled("true")
                        .updatePolicy("never")
                        .build())
                .build();
    }

    private Repository createSnapshotRepository(String id, String url) {
        return Repository.newBuilder()
                .id(id)
                .url(url)
                .snapshots(RepositoryPolicy.newBuilder()
                        .enabled("true")
                        .updatePolicy("daily")
                        .build())
                .releases(RepositoryPolicy.newBuilder().enabled("false").build())
                .build();
    }

    private void addRepository(Project project, Repository r) {
        repositories.get(project.getPomPath()).add(r);
    }

    private void addEmptyRepository(Project project) {
        repositories.get(project.getPomPath()).clear();
    }

    private void addPluginRepository(Project project, Repository r) {
        pluginRepositories.get(project.getPomPath()).add(r);
    }

    private void addEmptyPluginRepository(Project project) {
        pluginRepositories.get(project.getPomPath()).clear();
    }

    @Test
    void testAllBannedNoRepositories() throws EnforcerRuleException {
        Project baseProject = createStandAloneProject();
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * The model contains a single repository which is not allowed by the default rules.
     */
    @Test
    void testAllBannedWithRepository() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project baseProject = createStandAloneProject();
            addRepository(baseProject, createRepository("repo", "http://example.com/repo"));
            setupSortedProjects(Collections.singletonList(baseProject));

            rule.execute();
        });
    }

    /**
     * The model contains a single plugin repository which is not allowed by the default rules.
     */
    @Test
    void testAllBannedWithPluginRepository() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project baseProject = createStandAloneProject();
            addPluginRepository(baseProject, createRepository("repo", "http://example.com/repo"));
            setupSortedProjects(Collections.singletonList(baseProject));

            rule.execute();
        });
    }

    /**
     * The model contains a single repository which is allowed by setting allowedRepositories to the id.
     */
    @Test
    void testAllBannedWithAllowedRepositories() throws EnforcerRuleException {
        final String repositoryId = "repo";
        rule.setAllowedRepositories(Collections.singletonList(repositoryId));

        Project baseProject = createStandAloneProject();
        addRepository(baseProject, createRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * The model contains a single repository. Turned off ban repositories.
     */
    @Test
    void testRepositoriesNotBannedWithSingleRepository() throws EnforcerRuleException {
        final String repositoryId = "repo";

        rule.setBanRepositories(false);

        Project baseProject = createStandAloneProject();
        addRepository(baseProject, createRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * The model contains no repository at all. Turned off ban repositories.
     */
    @Test
    void testRepositoriesNotBannedWithOutAnyRepository() throws EnforcerRuleException {
        rule.setBanRepositories(false);

        Project baseProject = createStandAloneProject();
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * This model contains a single plugin repository. The given plugin repository is added to the list of allowed
     * plugin repositories.
     */
    @Test
    void testAllBannedWithAllowedPluginRepositories() throws EnforcerRuleException {
        final String repositoryId = "repo";
        rule.setAllowedPluginRepositories(Collections.singletonList(repositoryId));

        Project baseProject = createStandAloneProject();
        addPluginRepository(baseProject, createRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * The model contains a single plugin repository. Turned off ban plugin repositories.
     */
    @Test
    void testPluginRepositoriesNotBannedWithSinglePluginRepository() throws EnforcerRuleException {
        final String repositoryId = "repo";

        rule.setBanPluginRepositories(false);

        Project baseProject = createStandAloneProject();
        addPluginRepository(baseProject, createRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    /**
     * The model contains no repository at all. Turned off ban plugin repositories.
     */
    @Test
    void testPluginRepositoriesNotBannedWithOutAnyRepository() throws EnforcerRuleException {
        rule.setBanPluginRepositories(false);

        Project baseProject = createStandAloneProject();
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    @Test
    void testAllBannedWithSnapshotRepository() {
        assertThrows(EnforcerRuleException.class, () -> {
            Project baseProject = createStandAloneProject();
            addRepository(baseProject, createSnapshotRepository("repo", "http://example.com/repo"));
            setupSortedProjects(Collections.singletonList(baseProject));

            rule.execute();
        });
    }

    @Test
    void testAllBannedWithSnapshotRepositoryAllowedRepositories() throws EnforcerRuleException {
        final String repositoryId = "repo";
        rule.setAllowedRepositories(Collections.singletonList(repositoryId));

        Project baseProject = createStandAloneProject();
        addRepository(baseProject, createSnapshotRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    @Test
    void testAllBannedWithSnapshotRepositoryAndSetAllowSnapshotRepositories() throws EnforcerRuleException {
        final String repositoryId = "repo";
        rule.setAllowSnapshotRepositories(true);

        Project baseProject = createStandAloneProject();
        addRepository(baseProject, createSnapshotRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    @Test
    void testAllBannedWithSnapshotPluginRepositoryAndSetAllowSnapshotPluginRepositories() throws EnforcerRuleException {
        final String repositoryId = "repo";
        rule.setAllowSnapshotPluginRepositories(true);

        Project baseProject = createStandAloneProject();
        addPluginRepository(baseProject, createSnapshotRepository(repositoryId, "http://example.com/repo"));
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    @Test
    void testAllBannedWithEmptyRepository() throws EnforcerRuleException {
        Project baseProject = createStandAloneProject();
        addEmptyRepository(baseProject);
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }

    @Test
    void testAllBannedWithEmptyPluginRepository() throws EnforcerRuleException {
        Project baseProject = createStandAloneProject();
        addEmptyPluginRepository(baseProject);
        setupSortedProjects(Collections.singletonList(baseProject));

        rule.execute();
    }
}
