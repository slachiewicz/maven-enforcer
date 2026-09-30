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

import java.util.Objects;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.ArtifactUtils;

/**
 * This rule checks that no snapshots are included.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@Named("requireReleaseDeps")
public final class RequireReleaseDeps extends BannedDependenciesBase {

    /**
     * Allows this rule to execute only when this project is a release.
     */
    private boolean onlyWhenRelease = false;

    /**
     * Allows this rule to fail when the parent is defined as a snapshot.
     */
    private boolean failWhenParentIsSnapshot = true;

    private Predicate<Artifact> shouldExclude;
    private Predicate<Artifact> shouldInclude;

    private final Project project;

    @Inject
    public RequireReleaseDeps(Project project, ResolverUtil resolverUtil, VersionParser versionParser) {
        super(resolverUtil, versionParser);
        this.project = Objects.requireNonNull(project);
    }

    // Override parent to allow optional ignore of this rule.
    @Override
    public void execute() throws EnforcerRuleException {
        boolean callSuper;
        if (onlyWhenRelease) {
            // only call super if this project is a release
            callSuper = !project.getPomArtifact().isSnapshot();
        } else {
            callSuper = true;
        }

        if (callSuper) {
            shouldExclude = ArtifactUtils.prepareDependencyArtifactMatcher(getVersionParser(), getExcludes());
            shouldInclude = ArtifactUtils.prepareDependencyArtifactMatcher(getVersionParser(), getIncludes());
            super.execute();
            if (failWhenParentIsSnapshot) {

                Parent parent = project.getModel().getParent();

                if (parent != null && isIncluded(parent) && getVersionParser().isSnapshot(parent.getVersion())) {
                    throw new EnforcerRuleException("Parent Cannot be a snapshot: " + parent.getGroupId() + ":"
                            + parent.getArtifactId() + ":pom:" + parent.getVersion());
                }
            }
        }
    }

    /**
     * The parent is checked only if it matches the includes and does not match the excludes.
     */
    private boolean isIncluded(Parent parent) {
        if (getIncludes() != null
                && !ArtifactUtils.matchesAny(
                        getVersionParser(),
                        getIncludes(),
                        parent.getGroupId(),
                        parent.getArtifactId(),
                        parent.getVersion(),
                        "pom")) {
            return false;
        }
        return getExcludes() == null
                || !ArtifactUtils.matchesAny(
                        getVersionParser(),
                        getExcludes(),
                        parent.getGroupId(),
                        parent.getArtifactId(),
                        parent.getVersion(),
                        "pom");
    }

    @Override
    protected String getErrorMessage() {
        return "is not a release dependency";
    }

    @Override
    protected boolean validate(Artifact artifact) {
        // only check isSnapshot() if the artifact does not match (excludes minus includes)
        // otherwise true
        return shouldExclude.test(artifact) && !shouldInclude.test(artifact) || !artifact.isSnapshot();
    }

    /**
     * Filter the dependency artifacts according to the includes and excludes
     * If includes and excludes are both null, the original set is returned.
     *
     * @param dependencies the list of dependencies to filter
     * @return the resulting set of dependencies
     */
    public void setOnlyWhenRelease(boolean onlyWhenRelease) {
        this.onlyWhenRelease = onlyWhenRelease;
    }

    public void setFailWhenParentIsSnapshot(boolean failWhenParentIsSnapshot) {
        this.failWhenParentIsSnapshot = failWhenParentIsSnapshot;
    }

    @Override
    public String toString() {
        return String.format(
                "RequireReleaseDeps[message=%s, excludes=%s, includes=%s, searchTransitive=%b, onlyWhenRelease=%b, failWhenParentIsSnapshot=%b]",
                getMessage(),
                getExcludes(),
                getIncludes(),
                isSearchTransitive(),
                onlyWhenRelease,
                failWhenParentIsSnapshot);
    }
}
