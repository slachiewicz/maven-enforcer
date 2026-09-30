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
import java.util.List;

import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.api.services.DependencyResolverRequest;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResolverUtilTest {

    private Session session;

    private Project project;

    private DependencyResolver dependencyResolver;

    private DependencyResolverResult result;

    private DependencyCoordinates compileDependency;

    private DependencyCoordinates optionalDependency;

    private DependencyCoordinates testDependency;

    private DependencyCoordinates managed;

    private ResolverUtil resolverUtil;

    private static DependencyCoordinates dependency(DependencyScope scope, boolean optional) {
        DependencyCoordinates dependency = mock(DependencyCoordinates.class);
        when(dependency.getScope()).thenReturn(scope);
        when(dependency.getOptional()).thenReturn(optional);
        return dependency;
    }

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        project = mock(Project.class);
        ProjectManager projectManager = mock(ProjectManager.class);
        dependencyResolver = mock(DependencyResolver.class);
        result = mock(DependencyResolverResult.class);

        compileDependency = dependency(DependencyScope.COMPILE, false);
        optionalDependency = dependency(DependencyScope.COMPILE, true);
        testDependency = dependency(DependencyScope.TEST, false);
        managed = mock(DependencyCoordinates.class);

        when(project.getDependencies())
                .thenReturn(Arrays.asList(compileDependency, optionalDependency, testDependency));
        when(project.getManagedDependencies()).thenReturn(Collections.singletonList(managed));
        when(project.getPomArtifact()).thenReturn(mock(ProducedArtifact.class));
        List<RemoteRepository> repositories = Collections.singletonList(mock(RemoteRepository.class));
        when(projectManager.getRemoteProjectRepositories(project)).thenReturn(repositories);
        when(session.getService(DependencyResolver.class)).thenReturn(dependencyResolver);
        when(dependencyResolver.resolve(any(DependencyResolverRequest.class))).thenReturn(result);
        when(result.getRoot()).thenReturn(mock(Node.class));

        resolverUtil = new ResolverUtil(session, project, projectManager);
    }

    private DependencyResolverRequest captureRequest() {
        ArgumentCaptor<DependencyResolverRequest> captor = ArgumentCaptor.forClass(DependencyResolverRequest.class);
        verify(dependencyResolver).resolve(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldSkipTestProvidedScopeAndOptionalByDefaultAndCollect() throws Exception {
        Node root = resolverUtil.resolveTransitiveDependencies();

        assertThat(root).isSameAs(result.getRoot());
        DependencyResolverRequest request = captureRequest();
        assertThat(request.getRequestType()).isEqualTo(DependencyResolverRequest.RequestType.COLLECT);
        assertThat(request.getDependencies()).containsExactly(compileDependency);
        assertThat(request.getManagedDependencies()).containsExactly(managed);
        assertThat(request.getRootArtifact()).isPresent();
        assertThat(request.getRepositories()).hasSize(1);
        assertThat(request.getVerbose()).isFalse();
        assertThat(request.getPathScope()).isEqualTo(PathScope.TEST_RUNTIME);
    }

    @Test
    void shouldSkipOptionalDependencies() throws Exception {
        resolverUtil.resolveTransitiveDependencies(true, Collections.emptyList());

        assertThat(captureRequest().getDependencies()).containsExactly(compileDependency, testDependency);
    }

    @Test
    void shouldKeepOptionalDependencies() throws Exception {
        resolverUtil.resolveTransitiveDependencies(false, Collections.emptyList());

        assertThat(captureRequest().getDependencies())
                .containsExactly(compileDependency, optionalDependency, testDependency);
    }

    @Test
    void shouldCollectVerbose() throws Exception {
        resolverUtil.resolveTransitiveDependenciesVerbose(Collections.emptyList());

        DependencyResolverRequest request = captureRequest();
        assertThat(request.getVerbose()).isTrue();
        assertThat(request.getRequestType()).isEqualTo(DependencyResolverRequest.RequestType.COLLECT);
        assertThat(request.getDependencies()).containsExactly(compileDependency, testDependency);
    }

    @Test
    void shouldResolve() throws Exception {
        resolverUtil.resolveDependencies(false, true, false, Collections.emptyList());

        assertThat(captureRequest().getRequestType()).isEqualTo(DependencyResolverRequest.RequestType.RESOLVE);
    }

    @Test
    void shouldReportResolverException() {
        when(dependencyResolver.resolve(any(DependencyResolverRequest.class)))
                .thenThrow(new DependencyResolverException("no way", new Exception()));

        assertThatThrownBy(() -> resolverUtil.resolveTransitiveDependencies())
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageStartingWith("Could not build dependency tree no way");
    }
}
