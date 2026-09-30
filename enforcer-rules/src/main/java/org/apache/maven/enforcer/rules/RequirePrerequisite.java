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

import java.util.List;
import java.util.Objects;

import org.apache.maven.api.Project;
import org.apache.maven.api.Version;
import org.apache.maven.api.VersionConstraint;
import org.apache.maven.api.VersionRange;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Prerequisites;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.api.services.VersionParserException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * @author Robert Scholte
 * @since 1.3
 */
@Named("requirePrerequisite")
public final class RequirePrerequisite extends AbstractStandardEnforcerRule {
    /**
     * Only the projects with one of these packagings will be enforced to have the correct prerequisite.
     *
     * @since 1.4
     */
    private List<String> packagings;

    /**
     * Can either be version or a range, e.g. {@code 2.2.1} or {@code [2.2.1,)}
     */
    private String mavenVersion;

    private final Project project;

    private final VersionParser versionParser;

    @Inject
    public RequirePrerequisite(Project project, VersionParser versionParser) {
        this.project = Objects.requireNonNull(project);
        this.versionParser = Objects.requireNonNull(versionParser);
    }

    /**
     * Set the mavenVersion Can either be version or a range, e.g. {@code 2.2.1} or {@code [2.2.1,)}
     *
     * @param mavenVersion the version or {@code null}
     */
    public void setMavenVersion(String mavenVersion) {
        this.mavenVersion = mavenVersion;
    }

    /**
     * Only the projects with one of these packagings will be enforced to have the correct prerequisite.
     *
     * @since 1.4
     * @param packagings the list of packagings
     */
    public void setPackagings(List<String> packagings) {
        this.packagings = packagings;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        try {

            String packaging = project.getModel().getPackaging();

            if ("pom".equals(packaging)) {
                getLog().debug("Packaging is pom, skipping requirePrerequisite rule");
                return;
            }

            if (packagings != null && !packagings.contains(packaging)) {
                getLog().debug("Packaging is " + packaging + ", skipping requirePrerequisite rule");
                return;
            }

            Prerequisites prerequisites = project.getModel().getPrerequisites();

            if (prerequisites == null) {
                StringBuilder buf = new StringBuilder();
                if (getMessage() != null) {
                    buf.append(getMessage()).append("\n");
                }
                buf.append("Requires prerequisite not set");
                throw new EnforcerRuleException(buf.toString());
            }

            if (mavenVersion != null) {

                VersionConstraint requiredVersionRange = versionParser.parseVersionConstraint(mavenVersion);

                if (requiredVersionRange.getVersionRange() == null) {
                    requiredVersionRange = versionParser.parseVersionConstraint("[" + mavenVersion + ",)");
                }

                VersionConstraint specifiedVersion = versionParser.parseVersionConstraint(prerequisites.getMaven());

                if (!isSatisfied(requiredVersionRange, specifiedVersion)) {
                    StringBuilder buf = new StringBuilder();
                    if (getMessage() != null) {
                        buf.append(getMessage()).append("\n");
                    }
                    buf.append("The specified Maven prerequisite( " + specifiedVersion
                            + " ) doesn't match the required version: " + mavenVersion);
                    throw new EnforcerRuleException(buf.toString());
                }
            }
        } catch (VersionParserException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    /**
     * A single version is satisfied if it is inside the required range, a range is compared by its inclusive
     * boundaries.
     */
    private static boolean isSatisfied(VersionConstraint required, VersionConstraint specified) {
        if (specified.getRecommendedVersion() != null) {
            return required.contains(specified.getRecommendedVersion());
        }
        VersionRange range = specified.getVersionRange();
        for (VersionRange.Boundary boundary :
                new VersionRange.Boundary[] {range.getLowerBoundary(), range.getUpperBoundary()}) {
            if (boundary != null && boundary.isInclusive()) {
                Version version = boundary.getVersion();
                if (required.contains(version)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("RequirePrerequisite[packagings=%s, mavenVersion=%s]", packagings, mavenVersion);
    }
}
