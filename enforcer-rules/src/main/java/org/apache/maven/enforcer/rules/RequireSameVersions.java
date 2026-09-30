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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginManagement;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * @author Robert Scholte
 * @since 1.3
 */
@Named("requireSameVersions")
public final class RequireSameVersions extends AbstractStandardEnforcerRule {
    private boolean uniqueVersions;

    private Set<String> dependencies = new HashSet<>();

    private Set<String> plugins = new HashSet<>();

    private Set<String> buildPlugins = new HashSet<>();

    private Set<String> reportPlugins = new HashSet<>();

    private boolean sameModuleVersions;

    private final Project project;

    private final Session session;

    @Inject
    public RequireSameVersions(Project project, Session session) {
        this.project = Objects.requireNonNull(project);
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public void execute() throws EnforcerRuleException {
        // consider including profile based artifacts
        Map<String, List<String>> versionMembers = new LinkedHashMap<>();

        Set<String> allBuildPlugins = new HashSet<>(buildPlugins);
        allBuildPlugins.addAll(plugins);
        Set<String> allReportPlugins = new HashSet<>(reportPlugins);
        allReportPlugins.addAll(plugins);
        // CHECKSTYLE_OFF: LineLength
        versionMembers.putAll(collectVersionMembers(
                getDependencyArtifacts(),
                dependencies,
                " (dependency)",
                artifact -> uniqueVersions ? artifact.version : artifact.baseVersion));

        versionMembers.putAll(collectVersionMembers(
                getPluginArtifacts(),
                allBuildPlugins,
                " (buildPlugin)",
                artifact -> uniqueVersions ? artifact.version : artifact.baseVersion));

        versionMembers.putAll(collectVersionMembers(
                getReportArtifacts(), allReportPlugins, " (reportPlugin)", this::getReportPluginVersion));
        // CHECKSTYLE_ON: LineLength

        if (versionMembers.size() > 1) {
            StringBuilder builder = new StringBuilder();
            if (getMessage() != null) {
                builder.append(getMessage()).append("\n");
            }
            builder.append("Found entries with different versions" + "\n");
            for (Map.Entry<String, List<String>> entry : versionMembers.entrySet()) {
                builder.append("Entries with version ").append(entry.getKey()).append("\n");
                for (String conflictId : entry.getValue()) {
                    builder.append("- ").append(conflictId).append("\n");
                }
            }
            throw new EnforcerRuleException(builder.toString());
        }

        if (sameModuleVersions) {
            Project topLevelProject = session.getProjects().stream()
                    .filter(Project::isTopProject)
                    .findFirst()
                    .orElse(project);
            if (!Objects.equals(topLevelProject.getVersion(), project.getVersion())) {
                StringBuilder builder = new StringBuilder();
                if (getMessage() != null) {
                    builder.append(getMessage()).append("\n");
                }
                builder.append("Top level project has version " + topLevelProject.getVersion()
                        + " but current module has different version " + project.getVersion());
                throw new EnforcerRuleException(builder.toString());
            }
        }
    }

    private Map<String, List<String>> collectVersionMembers(
            Collection<ArtifactInfo> artifacts,
            Collection<String> patterns,
            String source,
            Function<ArtifactInfo, String> versionResolver) {
        Map<String, List<String>> versionMembers = new LinkedHashMap<>();

        List<Pattern> regExs = new ArrayList<>();
        for (String pattern : patterns) {
            String regex = pattern.replace(".", "\\.")
                    .replace("*", ".*")
                    .replace(":", "\\:")
                    .replace('?', '.');

            // pattern is groupId[:artifactId[:type[:classifier]]]
            regExs.add(Pattern.compile(regex + "(\\:.+)?"));
        }

        for (ArtifactInfo artifact : artifacts) {
            for (Pattern regEx : regExs) {
                if (regEx.matcher(artifact.conflictId).matches()) {
                    String version = versionResolver.apply(artifact);
                    versionMembers
                            .computeIfAbsent(version, unused -> new ArrayList<>())
                            .add(artifact.conflictId + source);
                }
            }
        }
        return versionMembers;
    }

    private List<ArtifactInfo> getDependencyArtifacts() {
        Node root = session.collectDependencies(project, PathScope.TEST_RUNTIME);
        List<ArtifactInfo> result = new ArrayList<>();
        for (Node node : session.flattenDependencies(root, PathScope.TEST_RUNTIME)) {
            org.apache.maven.api.Dependency dependency = node.getDependency();
            if (dependency != null) {
                String conflictId = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                        + dependency.getType().id()
                        + (dependency.getClassifier() != null
                                        && !dependency.getClassifier().isEmpty()
                                ? ":" + dependency.getClassifier()
                                : "");
                result.add(new ArtifactInfo(
                        conflictId,
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion().toString(),
                        dependency.getBaseVersion().toString()));
            }
        }
        return result;
    }

    private List<ArtifactInfo> getPluginArtifacts() {
        Build build = project.getModel().getBuild();
        List<ArtifactInfo> result = new ArrayList<>();
        if (build != null) {
            for (Plugin plugin : build.getPlugins()) {
                result.add(pluginArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
            }
        }
        return result;
    }

    private List<ArtifactInfo> getReportArtifacts() {
        Reporting reporting = project.getModel().getReporting();
        List<ArtifactInfo> result = new ArrayList<>();
        if (reporting != null) {
            for (ReportPlugin plugin : reporting.getPlugins()) {
                result.add(pluginArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
            }
        }
        return result;
    }

    private static ArtifactInfo pluginArtifact(String groupId, String artifactId, String version) {
        String v = version == null || version.isEmpty() ? "RELEASE" : version;
        return new ArtifactInfo(groupId + ":" + artifactId + ":maven-plugin", groupId, artifactId, v, v);
    }

    private String getReportPluginVersion(ArtifactInfo artifact) {
        Reporting reporting = project.getModel().getReporting();
        if (reporting != null) {
            for (ReportPlugin reportPlugin : reporting.getPlugins()) {
                if (Objects.equals(artifact.groupId, reportPlugin.getGroupId())
                        && Objects.equals(artifact.artifactId, reportPlugin.getArtifactId())
                        && reportPlugin.getVersion() != null) {
                    return reportPlugin.getVersion();
                }
            }
        }
        Build build = project.getModel().getBuild();
        if (build != null) {
            for (Plugin plugin : build.getPlugins()) {
                if (Objects.equals(artifact.groupId, plugin.getGroupId())
                        && Objects.equals(artifact.artifactId, plugin.getArtifactId())
                        && plugin.getVersion() != null) {
                    return plugin.getVersion();
                }
            }
        }
        PluginManagement pluginManagement = build != null ? build.getPluginManagement() : null;

        if (pluginManagement != null) {
            for (Plugin plugin : pluginManagement.getPlugins()) {
                if (Objects.equals(artifact.groupId, plugin.getGroupId())
                        && Objects.equals(artifact.artifactId, plugin.getArtifactId())
                        && plugin.getVersion() != null) {
                    return plugin.getVersion();
                }
            }
        }

        return uniqueVersions ? artifact.version : artifact.baseVersion;
    }

    private static final class ArtifactInfo {
        private final String conflictId;
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String baseVersion;

        ArtifactInfo(String conflictId, String groupId, String artifactId, String version, String baseVersion) {
            this.conflictId = conflictId;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.baseVersion = baseVersion;
        }
    }

    void addDependency(String dependency) {
        dependencies.add(dependency);
    }

    void addPlugin(String plugin) {
        plugins.add(plugin);
    }

    void addBuildPlugin(String buildPlugin) {
        buildPlugins.add(buildPlugin);
    }

    void addReportPlugin(String reportPlugin) {
        reportPlugins.add(reportPlugin);
    }

    @Override
    public String toString() {
        return String.format(
                "RequireSameVersions[dependencies=%s, buildPlugins=%s, reportPlugins=%s, plugins=%s, uniqueVersions=%b, sameModuleVersions=%b]",
                dependencies, buildPlugins, reportPlugins, plugins, uniqueVersions, sameModuleVersions);
    }
}
