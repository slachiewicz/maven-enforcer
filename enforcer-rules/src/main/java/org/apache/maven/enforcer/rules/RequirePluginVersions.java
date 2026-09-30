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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Lifecycle;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.BuildBase;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.ModelBase;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginConfiguration;
import org.apache.maven.api.model.PluginContainer;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.LifecycleRegistry;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.EnforcerRuleUtils;
import org.apache.maven.enforcer.rules.utils.ExpressionEvaluator;
import org.apache.maven.enforcer.rules.utils.PluginWrapper;

import static java.util.Optional.ofNullable;

/**
 * This rule will enforce that all plugins specified in the poms have a version declared.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@Named("requirePluginVersions")
public final class RequirePluginVersions extends AbstractStandardEnforcerRule {

    private static final String SNAPSHOT_VERSION = "SNAPSHOT";

    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile("^(.*)-([0-9]{8}\\.[0-9]{6})-([0-9]+)$");

    /**
     * Don't allow the LATEST identifier.
     */
    private boolean banLatest = true;

    /**
     * Don't allow the RELEASE identifier.
     */
    private boolean banRelease = true;

    /**
     * Don't allow snapshot plugins.
     */
    private boolean banSnapshots = true;

    /**
     * Don't allow timestamp snapshot plugins.
     */
    private boolean banTimestamps = true;

    /**
     * @since 3.0.0
     */
    private boolean banMavenDefaults = true;

    /**
     * The comma separated list of phases that should be used to find lifecycle plugin bindings. The default value is
     * "clean,deploy,site".
     */
    private String phases = "clean,deploy,site";

    /**
     * Additional plugins to enforce have versions. These are plugins that may not be in the poms but are used anyway,
     * like help, eclipse etc. <br>
     * The plugins should be specified in the form: <code>group:artifactId</code>.
     */
    private List<String> additionalPlugins;

    /**
     * Plugins to skip for version enforcement. The plugins should be specified in the form:
     * <code>group:artifactId</code>. NOTE: This is deprecated, use unCheckedPluginList instead.
     */
    private List<String> unCheckedPlugins;

    /**
     * Same as unCheckedPlugins but as a comma list to better support properties. Sample form:
     * <code>group:artifactId,group2:artifactId2</code>
     *
     * @since 1.0-beta-1
     */
    private String unCheckedPluginList;

    /** The phase to lifecycle map. */
    private Map<String, Lifecycle> phaseToLifecycleMap;

    private final Session session;

    private final ProjectManager projectManager;

    private final LifecycleRegistry lifecycleRegistry;

    private final EnforcerRuleUtils utils;

    private final Project project;

    private final ExpressionEvaluator evaluator;

    @Inject
    public RequirePluginVersions(
            Session session,
            ProjectManager projectManager,
            LifecycleRegistry lifecycleRegistry,
            EnforcerRuleUtils utils,
            Project project,
            ExpressionEvaluator evaluator) {
        this.session = Objects.requireNonNull(session);
        this.projectManager = Objects.requireNonNull(projectManager);
        this.lifecycleRegistry = Objects.requireNonNull(lifecycleRegistry);
        this.utils = Objects.requireNonNull(utils);
        this.project = Objects.requireNonNull(project);
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    @Override
    public void execute() throws EnforcerRuleException {

        // get all the plugins that are bound to the specified lifecycles
        Map<String, Plugin> allPlugins = getBoundPlugins(project, phases);

        // insert any additional plugins specified by the user.
        allPlugins = addAdditionalPlugins(allPlugins, additionalPlugins);
        allPlugins.putAll(getProfilePlugins(project));

        // pull out any we should skip
        allPlugins = removeUncheckedPlugins(combineUncheckedPlugins(unCheckedPlugins, unCheckedPluginList), allPlugins);

        // there's nothing to do here
        if (allPlugins.isEmpty()) {
            getLog().info("No plugin bindings found.");
            return;
        } else {
            getLog().debug("All Plugins in use: " + allPlugins.keySet());
        }

        // get all the plugins that are mentioned in the pom (and parents)
        List<PluginWrapper> pluginWrappers = getAllPluginEntries(project);

        for (PluginWrapper pluginWrapper : pluginWrappers) {
            getLog().debug("pluginWrappers: " + pluginWrapper.getGroupId() + ":" + pluginWrapper.getArtifactId() + ":"
                    + pluginWrapper.getVersion() + " source: " + pluginWrapper.getSource());
        }
        // now look for the versions that aren't valid and add to a list.
        List<Plugin> failures = new ArrayList<>();

        for (Plugin plugin : allPlugins.values()) {
            if (!hasValidVersionSpecified(plugin, pluginWrappers)) {
                failures.add(plugin);
            }
        }

        // if anything was found, log it then append the optional message.
        if (!failures.isEmpty()) {
            handleMessagesToTheUser(project, failures);
        }
    }

    private void handleMessagesToTheUser(Project project, List<Plugin> failures) throws EnforcerRuleException {
        StringBuilder newMsg = new StringBuilder();
        newMsg.append("Some plugins are missing valid versions or depend on Maven ");
        newMsg.append(session.getMavenVersion());
        newMsg.append(" defaults");
        handleBanMessages(newMsg);
        newMsg.append(System.lineSeparator());
        for (Plugin plugin : failures) {
            newMsg.append("   ");
            newMsg.append(plugin.getGroupId());
            newMsg.append(":");
            newMsg.append(plugin.getArtifactId());

            try {
                newMsg.append(". \tThe version currently in use is ");

                Plugin currentPlugin = findCurrentPlugin(plugin, project);

                if (currentPlugin == null) {
                    newMsg.append("unknown");
                } else {
                    newMsg.append(currentPlugin.getVersion());

                    if (PluginWrapper.isVersionFromDefaultLifecycleBindings(currentPlugin)
                            .orElse(false)) {
                        newMsg.append(" via default lifecycle bindings");
                    } else {
                        String msg = PluginWrapper.isVersionFromSuperpom(currentPlugin)
                                .filter(b -> b)
                                .map(t -> " via super POM")
                                // for Maven 3.6.0 or before (MNG-6593 / MNG-6600)
                                .orElse(" via super POM or default lifecycle bindings");
                        newMsg.append(msg);
                    }
                }
            } catch (Exception e) {
                // lots can go wrong here. Don't allow any issues trying to
                // determine the issue stop me
                getLog().debug("Exception while determining plugin Version " + e.getMessage());
                newMsg.append(". Unable to determine the plugin version.");
            }
            newMsg.append(System.lineSeparator());
        }
        String message = getMessage();
        if (message != null && !message.isEmpty()) {
            newMsg.append(message);
        }

        throw new EnforcerRuleException(newMsg.toString());
    }

    private void handleBanMessages(StringBuilder newMsg) {
        if (banLatest || banRelease || banSnapshots || banTimestamps) {
            List<String> banList = new ArrayList<>();
            if (banLatest) {
                banList.add("LATEST");
            }
            if (banRelease) {
                banList.add("RELEASE");
            }
            if (banSnapshots) {
                banList.add("SNAPSHOT");
                if (banTimestamps) {
                    banList.add("TIMESTAMP SNAPSHOT");
                }
            }
            if (!banList.isEmpty()) {
                newMsg.append(" (");
                newMsg.append(String.join(", ", banList));
                newMsg.append(" as plugin version are not allowed)");
            }
        }
    }

    /**
     * Remove the plugins that the user doesn't want to check.
     *
     * @param uncheckedPlugins
     * @param plugins
     * @return The plugins which have been removed.
     */
    Map<String, Plugin> removeUncheckedPlugins(Collection<String> uncheckedPlugins, Map<String, Plugin> plugins)
            throws EnforcerRuleError {
        if (uncheckedPlugins != null && !uncheckedPlugins.isEmpty()) {
            for (String pluginKey : uncheckedPlugins) {
                Plugin plugin = parsePluginString(pluginKey, "UncheckedPlugins");
                plugins.remove(plugin.getKey());
            }
        }
        return plugins;
    }

    /**
     * Combines the old Collection with the new comma separated list.
     *
     * @param uncheckedPlugins     a new collections
     * @param uncheckedPluginsList a list to merge
     * @return List of unchecked plugins.
     */
    public Collection<String> combineUncheckedPlugins(
            Collection<String> uncheckedPlugins, String uncheckedPluginsList) {
        // if the comma list is empty, then there's nothing to do here.
        if (uncheckedPluginsList != null && !uncheckedPluginsList.isEmpty()) {
            // make sure there is a collection to add to.
            if (uncheckedPlugins == null) {
                uncheckedPlugins = new HashSet<>();
            } else if (!uncheckedPlugins.isEmpty()) {
                getLog().warn("The parameter 'unCheckedPlugins' is deprecated. Use 'unCheckedPluginList' instead");
            }

            uncheckedPlugins.addAll(Arrays.asList(uncheckedPluginsList.split(",")));
        }
        return uncheckedPlugins;
    }

    /**
     * Add the additional plugins if they don't exist yet.
     *
     * @param existing   the existing plugins
     * @param additional the additional plugins
     * @return the additional and existing plugins
     * @throws EnforcerRuleError the enforcer error
     */
    public Map<String, Plugin> addAdditionalPlugins(Map<String, Plugin> existing, List<String> additional)
            throws EnforcerRuleError {
        if (additional != null) {
            if (existing == null) {
                existing = new LinkedHashMap<>();
            }
            for (String pluginString : additional) {
                Plugin plugin = parsePluginString(pluginString, "AdditionalPlugins");
                existing.putIfAbsent(plugin.getKey(), plugin);
            }
        }
        return existing;
    }

    /**
     * Helper method to parse and inject a Plugin.
     *
     * @param pluginString a plugin description to parse
     * @param field        a source of pluginString
     * @return the prepared plugin
     */
    private Plugin parsePluginString(String pluginString, String field) throws EnforcerRuleError {
        if (pluginString != null) {
            String[] pluginStrings = pluginString.split(":");
            if (pluginStrings.length == 2) {
                return Plugin.newBuilder()
                        .groupId(StringUtils.strip(pluginStrings[0]))
                        .artifactId(StringUtils.strip(pluginStrings[1]))
                        .build();
            } else {
                throw new EnforcerRuleError("Invalid " + field + " string: " + pluginString);
            }
        } else {
            throw new EnforcerRuleError("Invalid " + field + " null plugin string.");
        }
    }

    /**
     * Finds the plugins that are listed in active profiles.
     *
     * @param project the project
     * @return the profile plugins
     */
    public Map<String, Plugin> getProfilePlugins(Project project) {
        Map<String, Plugin> result = new LinkedHashMap<>();
        List<Profile> profiles = project.getEffectiveActiveProfiles();
        if (profiles != null && !profiles.isEmpty()) {
            for (Profile p : profiles) {
                BuildBase b = p.getBuild();
                if (b != null) {
                    List<Plugin> plugins = b.getPlugins();
                    if (plugins != null) {
                        plugins.forEach(plugin -> result.putIfAbsent(plugin.getKey(), plugin));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Given a plugin, this will retrieve the matching plugin artifact from the model.
     *
     * @param plugin  plugin to lookup
     * @param project project to search
     * @return matching plugin, <code>null</code> if not found.
     */
    private Plugin findCurrentPlugin(Plugin plugin, Project project) throws EnforcerRuleException {
        Build build = project.getModel().getBuild();
        Plugin found = build != null ? build.getPluginsAsMap().get(plugin.getKey()) : null;

        if (found == null) {
            try {
                session.resolveArtifact(
                        session.createArtifactCoordinates(plugin.getGroupId(), plugin.getArtifactId(), "LATEST", "jar"),
                        projectManager.getRemotePluginRepositories(project));
            } catch (ArtifactResolverException e) {
                throw new EnforcerRuleException("Unable to resolve the plugin " + plugin.getArtifactId(), e);
            }
            found = plugin.withVersion("LATEST");
        }

        return found;
    }

    /**
     * Gets the plugins that are bound to the defined phases. This does not find plugins bound in the pom to a phase
     * later than the plugin is executing.
     *
     * @param project   the project
     * @param phases the phases
     * @return the bound plugins
     * @throws PluginNotFoundException     the plugin not found exception
     * @throws LifecycleExecutionException the lifecycle execution exception
     */
    private Map<String, Plugin> getBoundPlugins(Project project, String phases) throws EnforcerRuleException {

        Map<String, Plugin> allPlugins = new LinkedHashMap<>();

        // lookup the bindings for all the passed in phases
        String[] lifecyclePhases = phases.split(",");
        for (int i = 0; i < lifecyclePhases.length; i++) {
            String lifecyclePhase = lifecyclePhases[i];
            if (lifecyclePhase != null && !lifecyclePhase.isEmpty()) {
                Lifecycle lifecycle = getLifecycleForPhase(lifecyclePhase);
                if (lifecycle == null) {
                    // swallow this because the user may have declared a phase that doesn't exist for every module.
                    continue;
                }
                getLog().debug("getBoundPlugins(): " + project.getId() + " " + lifecyclePhase + " " + lifecycle.id());
                getAllPlugins(project, lifecycle).forEach(allPlugins::putIfAbsent);
            }
        }
        return allPlugins;
    }

    /**
     * Checks for valid version specified. Checks to see if the version is specified for the plugin. Can optionally ban
     * "RELEASE" or "LATEST" even if specified.
     *
     * @param source         the source
     * @param pluginWrappers the plugins
     * @return true, if successful
     */
    public boolean hasValidVersionSpecified(Plugin source, List<PluginWrapper> pluginWrappers) {
        boolean found = false;
        boolean status = false;
        for (PluginWrapper plugin : pluginWrappers) {
            // find the matching plugin entry
            if (isMatchingPlugin(source, plugin)) {
                found = true;
                // found the entry. now see if the version is specified
                String version = plugin.getVersion();
                try {
                    version = (String) evaluator.evaluate(version);
                } catch (IllegalStateException e) {
                    return false;
                }

                if (isValidVersion(version)) {
                    getLog().debug("checking for notEmpty and notIsWhitespace(): " + version);
                    if (banRelease && version.equals("RELEASE")) {
                        return false;
                    }

                    if (banLatest && version.equals("LATEST")) {
                        return false;
                    }

                    if (banSnapshots && isSnapshot(version)) {
                        return false;
                    }
                    // the version was specified and not
                    // banned. It's ok. Keep looking through the list to make
                    // sure it's not using a banned version somewhere else.

                    status = true;

                    if (!banRelease && !banLatest && !banSnapshots) {
                        // no need to keep looking
                        break;
                    }
                }
            }
        }
        if (!found) {
            getLog().debug("plugin " + source.getGroupId() + ":" + source.getArtifactId() + " not found");
        }
        return status;
    }

    private boolean isValidVersion(String version) {
        return (version != null && !version.isEmpty()) && !StringUtils.isWhitespace(version);
    }

    private boolean isMatchingPlugin(Plugin source, PluginWrapper plugin) {
        return source.getArtifactId().equals(plugin.getArtifactId())
                && source.getGroupId().equals(plugin.getGroupId());
    }

    /**
     * Checks if is snapshot.
     *
     * @param baseVersion the base version
     * @return true, if is snapshot
     */
    private boolean isSnapshot(String baseVersion) {
        if (banTimestamps) {
            return VERSION_FILE_PATTERN.matcher(baseVersion).matches() || baseVersion.endsWith(SNAPSHOT_VERSION);
        } else {
            return baseVersion.endsWith(SNAPSHOT_VERSION);
        }
    }

    /*
     * Uses borrowed lifecycle code to get a list of all plugins bound to the lifecycle.
     */

    /**
     * Gets the all plugins.
     *
     * @param project   the project
     * @param lifecycle the lifecycle
     * @return the all plugins
     * @throws PluginNotFoundException     the plugin not found exception
     * @throws LifecycleExecutionException the lifecycle execution exception
     */
    private Map<String, Plugin> getAllPlugins(Project project, Lifecycle lifecycle) throws EnforcerRuleException {

        getLog().debug("RequirePluginVersions.getAllPlugins:");

        Map<String, Plugin> plugins = new LinkedHashMap<>();

        // first, the bindings defined by the lifecycle itself, for example clean and site
        lifecycle.allPhases().forEach(phase -> {
            getLog().debug("  lifecycle phase = " + phase.name());
            for (Plugin plugin : phase.plugins()) {
                addBinding(plugins, plugin);
            }
        });

        // then, bind those associated with the packaging
        Packaging packaging;
        try {
            packaging = session.requirePackaging(project.getModel().getPackaging());
        } catch (IllegalArgumentException e) {
            throw new EnforcerRuleException(
                    "Cannot find lifecycle mapping for packaging: '"
                            + project.getModel().getPackaging() + "'.",
                    e);
        }
        Set<String> lifecyclePhases = new HashSet<>(lifecycleRegistry.computePhases(lifecycle));
        for (Map.Entry<String, PluginContainer> entry : packaging.plugins().entrySet()) {
            // the phases of the packaging are the phases of the default lifecycle
            if (Lifecycle.DEFAULT.equals(lifecycle.id()) || lifecyclePhases.contains(entry.getKey())) {
                getLog().debug("  lifecycleMapping = " + entry.getKey());
                for (Plugin plugin : entry.getValue().getPlugins()) {
                    getLog().debug("    plugin = " + plugin.getKey());
                    addBinding(plugins, plugin);
                }
            }
        }

        Build build = project.getModel().getBuild();
        if (build != null) {
            build.getPlugins().forEach(plugin -> plugins.putIfAbsent(plugin.getKey(), plugin));
        }

        return plugins;
    }

    private static void addBinding(Map<String, Plugin> plugins, Plugin plugin) {
        // only coordinates without version are known for the bindings
        Plugin binding = Plugin.newBuilder()
                .groupId(plugin.getGroupId())
                .artifactId(plugin.getArtifactId())
                .build();
        plugins.putIfAbsent(binding.getKey(), binding);
    }

    /**
     * Gets the phase to lifecycle map.
     *
     * @return the phase to lifecycle map
     * @throws EnforcerRuleException if a phase is defined in more than one lifecycle
     */
    public Map<String, Lifecycle> getPhaseToLifecycleMap() throws EnforcerRuleException {
        if (phaseToLifecycleMap == null) {
            phaseToLifecycleMap = new HashMap<>();

            for (Lifecycle lifecycle : lifecycleRegistry) {
                List<String> lifecyclePhases = new ArrayList<>(lifecycleRegistry.computePhases(lifecycle));
                lifecycle.aliases().forEach(alias -> lifecyclePhases.add(alias.v3Phase()));
                for (String phase : lifecyclePhases) {
                    getLog().debug("getPhaseToLifecycleMap(): phase: " + phase);
                    Lifecycle prevLifecycle = phaseToLifecycleMap.get(phase);
                    if (prevLifecycle != null && prevLifecycle != lifecycle) {
                        throw new EnforcerRuleException("Phase '" + phase
                                + "' is defined in more than one lifecycle: '" + lifecycle.id() + "' and '"
                                + prevLifecycle.id() + "'");
                    } else {
                        phaseToLifecycleMap.put(phase, lifecycle);
                    }
                }
            }
        }
        return phaseToLifecycleMap;
    }

    private Lifecycle getLifecycleForPhase(String phase) throws EnforcerRuleException {
        return getPhaseToLifecycleMap().get(phase);
    }

    private List<PluginWrapper> getAllPluginEntries(Project project) {
        List<PluginWrapper> plugins = new ArrayList<>();
        // now find all the plugin entries, either in
        // build.plugins or build.pluginManagement.plugins, profiles.plugins and reporting

        getPlugins(plugins, project.getModel());
        getReportingPlugins(plugins, project.getModel());
        getPluginManagementPlugins(plugins, project.getModel());
        addPluginsInProfiles(plugins, project.getModel());

        return plugins;
    }

    private void addPluginsInProfiles(List<PluginWrapper> plugins, Model model) {
        List<Profile> profiles = ofNullable(model).map(Model::getProfiles).orElseGet(Collections::emptyList);
        for (Profile profile : profiles) {
            getProfilePlugins(plugins, profile);
            getProfileReportingPlugins(plugins, profile);
            getProfilePluginManagementPlugins(plugins, profile);
        }
    }

    private void getProfilePluginManagementPlugins(List<PluginWrapper> plugins, Profile profile) {
        List<Plugin> modelPlugins = ofNullable(profile)
                .map(Profile::getBuild)
                .map(PluginConfiguration::getPluginManagement)
                .map(PluginContainer::getPlugins)
                .orElseGet(Collections::emptyList);
        plugins.addAll(PluginWrapper.addAll(utils.resolvePlugins(modelPlugins), banMavenDefaults));
    }

    private void getProfileReportingPlugins(List<PluginWrapper> plugins, Profile profile) {
        List<ReportPlugin> modelReportPlugins = ofNullable(profile)
                .map(ModelBase::getReporting)
                .map(Reporting::getPlugins)
                .orElseGet(Collections::emptyList);
        // add the reporting plugins
        plugins.addAll(PluginWrapper.addAll(utils.resolveReportPlugins(modelReportPlugins), banMavenDefaults));
    }

    private void getProfilePlugins(List<PluginWrapper> plugins, Profile profile) {
        List<Plugin> modelPlugins = ofNullable(profile)
                .map(Profile::getBuild)
                .map(PluginContainer::getPlugins)
                .orElseGet(Collections::emptyList);
        plugins.addAll(PluginWrapper.addAll(utils.resolvePlugins(modelPlugins), banMavenDefaults));
    }

    private void getPlugins(List<PluginWrapper> plugins, Model model) {
        List<Plugin> modelPlugins = ofNullable(model)
                .map(Model::getBuild)
                .map(PluginContainer::getPlugins)
                .orElseGet(Collections::emptyList);
        plugins.addAll(PluginWrapper.addAll(utils.resolvePlugins(modelPlugins), banMavenDefaults));
    }

    private void getPluginManagementPlugins(List<PluginWrapper> plugins, Model model) {
        List<Plugin> modelPlugins = ofNullable(model)
                .map(Model::getBuild)
                .map(PluginConfiguration::getPluginManagement)
                .map(PluginContainer::getPlugins)
                .orElseGet(Collections::emptyList);
        plugins.addAll(PluginWrapper.addAll(utils.resolvePlugins(modelPlugins), banMavenDefaults));
    }

    private void getReportingPlugins(List<PluginWrapper> plugins, Model model) {
        List<ReportPlugin> modelReportPlugins = ofNullable(model)
                .map(ModelBase::getReporting)
                .map(Reporting::getPlugins)
                .orElseGet(Collections::emptyList);
        // add the reporting plugins
        plugins.addAll(PluginWrapper.addAll(utils.resolveReportPlugins(modelReportPlugins), banMavenDefaults));
    }

    /**
     * Sets the ban latest.
     *
     * @param banLatest the banLatest to set
     */
    public void setBanLatest(boolean banLatest) {
        this.banLatest = banLatest;
    }

    /**
     * Sets the ban release.
     *
     * @param banRelease the banRelease to set
     */
    public void setBanRelease(boolean banRelease) {
        this.banRelease = banRelease;
    }

    /**
     * Checks if is ban snapshots.
     *
     * @return the banSnapshots
     */
    public boolean isBanSnapshots() {
        return this.banSnapshots;
    }

    /**
     * Sets the ban snapshots.
     *
     * @param banSnapshots the banSnapshots to set
     */
    public void setBanSnapshots(boolean banSnapshots) {
        this.banSnapshots = banSnapshots;
    }

    /**
     * Sets the ban timestamps.
     *
     * @param banTimestamps the banTimestamps to set
     */
    public void setBanTimestamps(boolean banTimestamps) {
        this.banTimestamps = banTimestamps;
    }

    @Override
    public String toString() {
        return String.format(
                "RequirePluginVersions[message=%s, banLatest=%b, banRelease=%b, banSnapshots=%b, banTimestamps=%b, phases=%s, additionalPlugins=%s, unCheckedPluginList=%s, unCheckedPlugins=%s]",
                getMessage(),
                banLatest,
                banRelease,
                banSnapshots,
                banTimestamps,
                phases,
                additionalPlugins,
                unCheckedPluginList,
                unCheckedPlugins);
    }
}
