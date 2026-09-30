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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.settings.Profile;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.OriginalModels;

/**
 * This rule checks that this pom or its parents don't define a repository.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@Named("requireNoRepositories")
public final class RequireNoRepositories extends AbstractStandardEnforcerRule {

    private static final String VERSION = " version:";

    /**
     * Whether to ban non-plugin repositories. By default they are banned.
     *
     * @see #setBanRepositories(boolean)
     */
    private boolean banRepositories = true;

    /**
     * Whether to ban plugin repositories. By default they are banned.
     *
     * @see #setBanPluginRepositories(boolean)
     */
    private boolean banPluginRepositories = true;

    /**
     * Specify explicitly allowed non-plugin repositories. This is a list of ids.
     *
     * @see #setAllowedRepositories(List)
     */
    private List<String> allowedRepositories;

    /**
     * Specify explicitly allowed plugin repositories. This is a list of ids.
     *
     * @see #setAllowedPluginRepositories(List)
     */
    private List<String> allowedPluginRepositories;

    /**
     * Whether to allow repositories which only resolve snapshots. By default they are banned.
     *
     * @see #setAllowSnapshotRepositories(boolean)
     */
    private boolean allowSnapshotRepositories = false;

    /**
     * Whether to allow plugin repositories which only resolve snapshots. By default they are banned.
     *
     * @see #setAllowSnapshotPluginRepositories(boolean)
     */
    private boolean allowSnapshotPluginRepositories = false;

    private final Session session;

    private final ModelXmlFactory modelXmlFactory;

    @Inject
    public RequireNoRepositories(Session session, ModelXmlFactory modelXmlFactory) {
        this.session = Objects.requireNonNull(session);
        this.modelXmlFactory = Objects.requireNonNull(modelXmlFactory);
    }

    public void setBanRepositories(boolean banRepositories) {
        this.banRepositories = banRepositories;
    }

    public void setBanPluginRepositories(boolean banPluginRepositories) {
        this.banPluginRepositories = banPluginRepositories;
    }

    public void setAllowedRepositories(List<String> allowedRepositories) {
        this.allowedRepositories = allowedRepositories;
    }

    public void setAllowedPluginRepositories(List<String> allowedPluginRepositories) {
        this.allowedPluginRepositories = allowedPluginRepositories;
    }

    public void setAllowSnapshotRepositories(boolean allowSnapshotRepositories) {
        this.allowSnapshotRepositories = allowSnapshotRepositories;
    }

    public void setAllowSnapshotPluginRepositories(boolean allowSnapshotPluginRepositories) {
        this.allowSnapshotPluginRepositories = allowSnapshotPluginRepositories;
    }

    @Override
    public void execute() throws EnforcerRuleException {

        // Maven 4 Model contains repositories defined in settings.xml
        // As workaround we exclude repositories defined in settings.xml
        // https://issues.apache.org/jira/browse/MNG-7228
        if (banRepositories) {
            Collection<String> reposIdsFromSettings = getRepoIdsFromSettings(Profile::getRepositories);
            if (!reposIdsFromSettings.isEmpty()) {
                getLog().debug(() -> "Allow repositories from settings: " + reposIdsFromSettings);
            }

            allowedRepositories = Optional.ofNullable(allowedRepositories).orElseGet(ArrayList::new);
            allowedRepositories.addAll(reposIdsFromSettings);
        }

        if (banPluginRepositories) {
            Collection<String> reposIdsFromSettings = getRepoIdsFromSettings(Profile::getPluginRepositories);
            if (!reposIdsFromSettings.isEmpty()) {
                getLog().debug(() -> "Allow plugin repositories from settings: " + reposIdsFromSettings);
            }

            allowedPluginRepositories =
                    Optional.ofNullable(allowedPluginRepositories).orElseGet(ArrayList::new);
            allowedPluginRepositories.addAll(reposIdsFromSettings);
        }

        List<Project> sortedProjects = session.getProjects();

        List<Model> models = new ArrayList<>();
        for (Project mavenProject : sortedProjects) {
            getLog().debug("Scanning project: " + mavenProject.getGroupId() + ":" + mavenProject.getArtifactId()
                    + VERSION + mavenProject.getVersion());
            models.add(OriginalModels.read(modelXmlFactory, mavenProject));
        }

        List<Model> badModels = new ArrayList<>();

        StringBuilder newMsg = new StringBuilder();
        newMsg.append("Some poms have repositories defined:" + System.lineSeparator());

        for (Model model : models) {
            if (banRepositories) {
                List<Repository> repos = model.getRepositories();
                if (repos != null && !repos.isEmpty()) {
                    List<String> bannedRepos =
                            findBannedRepositories(repos, allowedRepositories, allowSnapshotRepositories);
                    if (!bannedRepos.isEmpty()) {
                        badModels.add(model);
                        newMsg.append(model.getGroupId() + ":" + model.getArtifactId() + VERSION + model.getVersion()
                                + " has repositories " + bannedRepos);
                    }
                }
            }
            if (banPluginRepositories) {
                List<Repository> repos = model.getPluginRepositories();
                if (repos != null && !repos.isEmpty()) {
                    List<String> bannedRepos =
                            findBannedRepositories(repos, allowedPluginRepositories, allowSnapshotPluginRepositories);
                    if (!bannedRepos.isEmpty()) {
                        badModels.add(model);
                        newMsg.append(model.getGroupId() + ":" + model.getArtifactId() + VERSION + model.getVersion()
                                + " has plugin repositories " + bannedRepos);
                    }
                }
            }
        }

        // if anything was found, log it then append the
        // optional message.
        if (!badModels.isEmpty()) {
            if (StringUtils.isNotEmpty(getMessage())) {
                newMsg.append(getMessage());
            }

            throw new EnforcerRuleException(newMsg.toString());
        }
    }

    private Collection<String> getRepoIdsFromSettings(
            Function<Profile, List<org.apache.maven.api.settings.Repository>> getRepositoriesFunc) {

        // the profiles activated on the command line are not available in the API, only the ones from settings.xml
        List<String> activeProfileIds = session.getSettings().getActiveProfiles();

        return session.getSettings().getProfiles().stream()
                .filter(p -> activeProfileIds.contains(p.getId()))
                .map(getRepositoriesFunc)
                .flatMap(Collection::stream)
                .map(org.apache.maven.api.settings.Repository::getId)
                .collect(Collectors.toSet());
    }

    private static List<String> findBannedRepositories(
            List<Repository> repos, List<String> allowedRepos, boolean allowSnapshots) {
        List<String> bannedRepos = new ArrayList<>(allowedRepos.size());
        for (Repository r : repos) {
            if (!allowedRepos.contains(r.getId())) {
                if (!allowSnapshots
                        || r.getReleases() == null
                        || r.getReleases().isEnabled()) {
                    // if we are not allowing snapshots and this repo is enabled for releases
                    // it is banned.  We don't care whether it is enabled for snapshots
                    // if you define a repo and don't enable it for anything, then we have nothing
                    // to worry about
                    bannedRepos.add(r.getId());
                }
            }
        }
        return bannedRepos;
    }

    @Override
    public String toString() {
        return String.format(
                "RequireNoRepositories[message=%s, banRepositories=%b, allowSnapshotRepositories=%b, allowedRepositories=%s, "
                        + "banPluginRepositories=%b, allowSnapshotPluginRepositories=%b, allowedPluginRepositories=%s]",
                getMessage(),
                banRepositories,
                allowSnapshotRepositories,
                allowedRepositories,
                banPluginRepositories,
                allowSnapshotPluginRepositories,
                allowedPluginRepositories);
    }
}
