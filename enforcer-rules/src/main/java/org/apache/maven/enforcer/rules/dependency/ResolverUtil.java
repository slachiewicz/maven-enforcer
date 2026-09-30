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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.api.services.DependencyResolverRequest;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Resolver helper class.
 */
@Named
class ResolverUtil {

    private static final String SCOPE_TEST = DependencyScope.TEST.id();

    private static final String SCOPE_PROVIDED = DependencyScope.PROVIDED.id();

    private final Session session;

    private final Project project;

    private final ProjectManager projectManager;

    @Inject
    ResolverUtil(Session session, Project project, ProjectManager projectManager) {
        this.session = Objects.requireNonNull(session);
        this.project = Objects.requireNonNull(project);
        this.projectManager = Objects.requireNonNull(projectManager);
    }

    Node resolveTransitiveDependenciesVerbose(List<String> excludedScopes) throws EnforcerRuleException {
        return resolveTransitiveDependencies(true, false, true, excludedScopes);
    }

    Node resolveTransitiveDependencies() throws EnforcerRuleException {
        return resolveTransitiveDependencies(false, false, true, Arrays.asList(SCOPE_TEST, SCOPE_PROVIDED));
    }

    Node resolveTransitiveDependencies(boolean excludeOptional, List<String> excludedScopes)
            throws EnforcerRuleException {
        return resolveTransitiveDependencies(false, false, excludeOptional, excludedScopes);
    }

    Node resolveTransitiveDependencies(boolean verbose, boolean excludeOptional, List<String> excludedScopes)
            throws EnforcerRuleException {
        return resolveTransitiveDependencies(verbose, false, excludeOptional, excludedScopes);
    }

    Node resolveTransitiveDependencies(
            boolean verbose, boolean resolve, boolean excludeOptional, List<String> excludedScopes)
            throws EnforcerRuleException {
        return resolveDependencies(verbose, resolve, excludeOptional, excludedScopes)
                .getRoot();
    }

    /**
     * Collects or resolves the dependencies of the current project.
     *
     * @param verbose if the nodes omitted for conflict should be kept in the tree
     * @param resolve if the artifacts should be downloaded
     * @param excludeOptional if the optional dependencies of the project should be skipped
     * @param excludedScopes the scopes of the dependencies of the project to skip
     * @return the result
     * @throws EnforcerRuleException if the dependencies cannot be collected or resolved
     */
    DependencyResolverResult resolveDependencies(
            boolean verbose, boolean resolve, boolean excludeOptional, List<String> excludedScopes)
            throws EnforcerRuleException {

        try {
            List<DependencyCoordinates> dependencies = project.getDependencies().stream()
                    .filter(d -> !(excludeOptional && Boolean.TRUE.equals(d.getOptional())))
                    .filter(d -> !excludedScopes.contains(d.getScope().id()))
                    .collect(Collectors.toList());

            DependencyResolverRequest request = DependencyResolverRequest.builder()
                    .session(session)
                    .requestType(
                            resolve
                                    ? DependencyResolverRequest.RequestType.RESOLVE
                                    : DependencyResolverRequest.RequestType.COLLECT)
                    .rootArtifact(project.getPomArtifact())
                    .dependencies(dependencies)
                    .managedDependencies(project.getManagedDependencies())
                    .repositories(projectManager.getRemoteProjectRepositories(project))
                    .verbose(verbose)
                    // all the scopes, the excluded ones are removed from the direct dependencies above
                    .pathScope(PathScope.TEST_RUNTIME)
                    .build();

            return session.getService(DependencyResolver.class).resolve(request);
        } catch (DependencyResolverException | ArtifactResolverException e) {
            throw new EnforcerRuleException("Could not build dependency tree " + e.getLocalizedMessage(), e);
        }
    }

    public CharSequence dumpTree(Node rootNode) {
        StringBuilder result = new StringBuilder(System.lineSeparator());

        rootNode.accept(new org.apache.maven.api.NodeVisitor() {
            String indent = "";

            @Override
            public boolean enter(Node node) {
                result.append(indent);
                result.append("Node: ").append(node.asString());
                result.append(System.lineSeparator());
                indent += "  ";
                return true;
            }

            @Override
            public boolean leave(Node node) {
                indent = indent.substring(0, indent.length() - 2);
                return true;
            }
        });

        return result;
    }
}
