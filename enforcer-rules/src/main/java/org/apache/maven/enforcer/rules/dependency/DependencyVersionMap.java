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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.enforcer.rules.utils.ArtifactUtils;
import org.apache.maven.enforcer.rules.utils.ParentNodeProvider;
import org.apache.maven.enforcer.rules.utils.ParentsVisitor;

/**
 * @author Brian Fox
 */
class DependencyVersionMap implements NodeVisitor, ParentNodeProvider {
    private final VersionParser versionParser;
    private ParentsVisitor parentsVisitor;
    private boolean uniqueVersions;
    private final Map<String, List<Node>> idsToNode = new HashMap<>();

    DependencyVersionMap(VersionParser versionParser) {
        this.versionParser = versionParser;
        this.parentsVisitor = new ParentsVisitor();
    }

    public DependencyVersionMap setUniqueVersions(boolean uniqueVersions) {
        this.uniqueVersions = uniqueVersions;
        return this;
    }

    @Override
    public boolean enter(Node node) {
        addDependency(node);
        return parentsVisitor.enter(node) && !containsConflicts(node);
    }

    @Override
    public boolean leave(Node node) {
        return parentsVisitor.leave(node);
    }

    @Override
    public Node getParent(Node node) {
        return parentsVisitor.getParent(node);
    }

    private String constructKey(Node node) {
        return constructKey(node.getArtifact());
    }

    private String constructKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    public void addDependency(Node node) {
        String key = constructKey(node);
        idsToNode.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
    }

    private String getVersion(Artifact artifact) {
        return (uniqueVersions ? artifact.getVersion() : artifact.getBaseVersion()).toString();
    }

    private boolean containsConflicts(Node node) {
        return containsConflicts(node.getArtifact());
    }

    private boolean containsConflicts(Artifact artifact) {
        return containsConflicts(idsToNode.get(constructKey(artifact)));
    }

    private boolean containsConflicts(List<Node> nodes) {
        String version = null;
        for (Node node : nodes) {
            if (version == null) {
                version = getVersion(node.getArtifact());
            } else {
                if (version.compareTo(getVersion(node.getArtifact())) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<List<Node>> getConflictedVersionNumbers(List<String> includes, List<String> excludes) {
        List<String> formattedIncludes = formatPatterns(includes);
        List<String> formattedExcludes = formatPatterns(excludes);
        List<List<Node>> output = new ArrayList<>();
        for (List<Node> nodes : idsToNode.values()) {
            List<Node> filteredNodes = nodes;
            if (formattedIncludes != null || formattedExcludes != null) {
                filteredNodes = new ArrayList<>();
                for (Node node : nodes) {
                    if (includeArtifact(node, formattedIncludes, formattedExcludes)) {
                        filteredNodes.add(node);
                    }
                }
            }
            if (containsConflicts(filteredNodes)) {
                output.add(filteredNodes);
            }
        }
        return output;
    }

    private boolean includeArtifact(Node node, List<String> includes, List<String> excludes) {
        boolean included = includes == null || includes.isEmpty();
        if (!included) {
            for (String pattern : includes) {
                if (ArtifactUtils.compareDependency(versionParser, pattern, ArtifactUtils.toArtifact(node))) {
                    included = true;
                    break;
                }
            }
        }
        if (!included) {
            return false;
        }
        boolean excluded = false;
        if (excludes != null) {
            for (String pattern : excludes) {
                if (ArtifactUtils.compareDependency(versionParser, pattern, ArtifactUtils.toArtifact(node))) {
                    excluded = true;
                    break;
                }
            }
        }
        return !excluded;
    }

    private static List<String> formatPatterns(List<String> patterns) {
        if (patterns == null) {
            return null;
        }
        List<String> formattedPatterns = new ArrayList<>();
        for (String pattern : patterns) {
            String[] subStrings = pattern.split(":");
            subStrings = StringUtils.stripAll(subStrings);
            String formattedPattern = StringUtils.join(subStrings, ":");
            formattedPatterns.add(formattedPattern);
        }
        return formattedPatterns;
    }
}
