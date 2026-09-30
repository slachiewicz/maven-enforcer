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

import java.util.Objects;

import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * This rule checks that the current project is not a snapshot.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@Named("requireReleaseVersion")
public final class RequireReleaseVersion extends AbstractStandardEnforcerRule {

    private final Project project;

    private final VersionParser versionParser;

    /**
     * Allows this rule to fail when the parent is defined as a snapshot.
     */
    private boolean failWhenParentIsSnapshot = true;

    @Inject
    public RequireReleaseVersion(Project project, VersionParser versionParser) {
        this.project = Objects.requireNonNull(project);
        this.versionParser = Objects.requireNonNull(versionParser);
    }

    @Override
    public void execute() throws EnforcerRuleException {

        if (project.getPomArtifact().isSnapshot()) {
            String message = getMessage();
            StringBuilder buf = new StringBuilder();
            if (message != null) {
                buf.append(message).append(System.lineSeparator());
            }
            buf.append("This project cannot be a snapshot:").append(project.getId());
            throw new EnforcerRuleException(buf.toString());
        }
        if (failWhenParentIsSnapshot) {
            Parent parent = project.getModel().getParent();
            if (parent != null && versionParser.isSnapshot(parent.getVersion())) {
                throw new EnforcerRuleException("Parent Cannot be a snapshot: " + parent.getGroupId() + ":"
                        + parent.getArtifactId() + ":pom:" + parent.getVersion());
            }
        }
    }

    public void setFailWhenParentIsSnapshot(boolean failWhenParentIsSnapshot) {
        this.failWhenParentIsSnapshot = failWhenParentIsSnapshot;
    }

    @Override
    public String toString() {
        return String.format("RequireReleaseVersion[failWhenParentIsSnapshot=%b]", failWhenParentIsSnapshot);
    }
}
