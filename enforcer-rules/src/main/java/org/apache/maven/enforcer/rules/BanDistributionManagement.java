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
import org.apache.maven.api.model.DistributionManagement;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.OriginalModels;

/**
 * This rule will check if a pom contains a <code>distributionManagement</code> part. This should be by best practice
 * only defined once. It could happen that you like to check the parent as well. This can be activated by using the
 * <code>ignoreParent</code> which is by default turned off (<code>true</code>) which means not to check the parent.
 *
 * @author Karl Heinz Marbaise
 * @since 1.4
 */
@Named("banDistributionManagement")
public final class BanDistributionManagement extends AbstractStandardEnforcerRule {

    /**
     * Allow using a repository entry in the distributionManagement area.
     */
    private boolean allowRepository = false;

    /**
     * Allow snapshotRepository entry in the distributionManagement area.
     */
    private boolean allowSnapshotRepository = false;

    /**
     * Allow site entry in the distributionManagement area.
     */
    private boolean allowSite = false;

    private final Project project;

    private final ModelXmlFactory modelXmlFactory;

    @Inject
    public BanDistributionManagement(Project project, ModelXmlFactory modelXmlFactory) {
        this.project = Objects.requireNonNull(project);
        this.modelXmlFactory = Objects.requireNonNull(modelXmlFactory);
    }

    @Override
    public void execute() throws EnforcerRuleException {

        if (project.isTopProject()) {
            if (!project.getParent().isPresent()) {
                // Does it make sense to check something? If yes please make a JIRA ticket for it.
                getLog().debug("We have no parent and in the root of a build we don't check anything,");
                getLog().debug("because that is the location where we defined maven-enforcer-plugin.");
            } else {
                getLog().debug("We are in the root of the execution and we have a parent.");

                DistributionManagementCheck check = new DistributionManagementCheck(project, modelXmlFactory);
                executeCheck(check);
            }
        } else {
            getLog().debug("We are in a deeper level.");
            DistributionManagementCheck check = new DistributionManagementCheck(project, modelXmlFactory);
            executeCheck(check);
        }
    }

    private void executeCheck(DistributionManagementCheck check) throws EnforcerRuleException {
        try {
            check.execute(isAllowRepository(), isAllowSnapshotRepository(), isAllowSite());
        } catch (EnforcerRuleException e) {
            if (getMessage() != null) {
                throw new EnforcerRuleException(getMessage() + "\n" + e.getMessage(), e);
            }
            throw e;
        }
    }

    public boolean isAllowRepository() {
        return allowRepository;
    }

    public void setAllowRepository(boolean allowRepository) {
        this.allowRepository = allowRepository;
    }

    public boolean isAllowSnapshotRepository() {
        return allowSnapshotRepository;
    }

    public void setAllowSnapshotRepository(boolean allowSnapshotRepository) {
        this.allowSnapshotRepository = allowSnapshotRepository;
    }

    public boolean isAllowSite() {
        return allowSite;
    }

    public void setAllowSite(boolean allowSite) {
        this.allowSite = allowSite;
    }

    private static class DistributionManagementCheck {
        private DistributionManagement distributionManagement;

        DistributionManagementCheck(Project project, ModelXmlFactory modelXmlFactory) throws EnforcerRuleException {
            this.distributionManagement =
                    OriginalModels.read(modelXmlFactory, project).getDistributionManagement();
        }

        public void execute(boolean isAllowRepository, boolean isAllowSnapshotRepository, boolean isAllowSite)
                throws EnforcerRuleException {
            if (hasDistributionManagement()) {
                if (!isAllowRepository && hasRepository()) {
                    throw new EnforcerRuleException("You have defined a repository in distributionManagement.");
                } else if (!isAllowSnapshotRepository && hasSnapshotRepository()) {
                    throw new EnforcerRuleException("You have defined a snapshotRepository in distributionManagement.");
                } else if (!isAllowSite && hasSite()) {
                    throw new EnforcerRuleException("You have defined a site in distributionManagement.");
                }
            }
        }

        private boolean hasRepository() {
            return getDistributionManagement().getRepository() != null;
        }

        public DistributionManagement getDistributionManagement() {
            return distributionManagement;
        }

        public void setDistributionManagement(DistributionManagement distributionManagement) {
            this.distributionManagement = distributionManagement;
        }

        private boolean hasSnapshotRepository() {
            return getDistributionManagement().getSnapshotRepository() != null;
        }

        private boolean hasSite() {
            return getDistributionManagement().getSite() != null;
        }

        private boolean hasDistributionManagement() {
            return getDistributionManagement() != null;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BanDistributionManagement[allowRepository=%b, allowSnapshotRepository=%b, allowSite=%b]",
                allowRepository, allowSnapshotRepository, allowSite);
    }
}
