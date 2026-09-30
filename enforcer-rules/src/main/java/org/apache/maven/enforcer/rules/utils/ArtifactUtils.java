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
package org.apache.maven.enforcer.rules.utils;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.Node;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.api.services.VersionParserException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import static java.util.Optional.ofNullable;

/**
 * Artifact related utilities.
 *
 * @author Slawomir Jaranowski
 * @since 3.2.0
 */
public final class ArtifactUtils {

    private ArtifactUtils() {}

    /**
     * The artifact of a node, it is a {@link Dependency} if the node has one, so scope and type are available.
     *
     * @param node the node
     * @return the artifact
     */
    public static Artifact toArtifact(Node node) {
        Dependency dependency = node.getDependency();
        return dependency != null ? dependency : node.getArtifact();
    }

    /**
     * Checks if the artifact coordinates match any of the patterns.
     *
     * @param versionParser the parser of versions
     * @param patterns the patterns
     * @param groupId the group id
     * @param artifactId the artifact id
     * @param version the version
     * @param type the type
     * @return {@code true} if any pattern matches
     */
    public static boolean matchesAny(
            VersionParser versionParser,
            Collection<String> patterns,
            String groupId,
            String artifactId,
            String version,
            String type) {
        return cleansePatterns(patterns)
                .map(p -> new ArtifactMatcher.Pattern(versionParser, p))
                .anyMatch(pattern -> pattern.match(groupId, artifactId, version, type, null, null));
    }

    /**
     * The identifier of an artifact in the format {@code groupId:artifactId:type[:classifier]:version}, the type is
     * the extension if the artifact is not a {@link Dependency}.
     *
     * @param artifact the artifact
     * @return the identifier
     */
    public static String getId(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + getType(artifact)
                + (hasClassifier(artifact) ? ":" + artifact.getClassifier() : "") + ":" + artifact.getBaseVersion();
    }

    /**
     * The string representation of an artifact in the format
     * {@code groupId:artifactId:type[:classifier]:version[:scope]}.
     *
     * @param artifact the artifact
     * @return the string representation
     */
    public static String toString(Artifact artifact) {
        StringBuilder sb = new StringBuilder(getId(artifact));
        // the id has base version, the string representation the version
        sb.setLength(sb.length() - artifact.getBaseVersion().toString().length());
        sb.append(artifact.getVersion());
        if (artifact instanceof Dependency) {
            sb.append(':').append(((Dependency) artifact).getScope().id());
        }
        return sb.toString();
    }

    /**
     * The identifier of an artifact in the format {@code groupId:artifactId:extension[:classifier]:version}.
     *
     * @param artifact the artifact
     * @return the identifier
     */
    public static String toId(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension()
                + (hasClassifier(artifact) ? ":" + artifact.getClassifier() : "") + ":" + artifact.getVersion();
    }

    private static String getType(Artifact artifact) {
        return artifact instanceof Dependency
                ? ((Dependency) artifact).getType().id()
                : artifact.getExtension();
    }

    private static boolean hasClassifier(Artifact artifact) {
        return artifact.getClassifier() != null && !artifact.getClassifier().isEmpty();
    }

    public static Set<Artifact> filterDependencyArtifacts(
            VersionParser versionParser, Set<Artifact> dependencies, Collection<String> patterns)
            throws EnforcerRuleException {
        try {
            return ofNullable(patterns)
                    .map(collection -> collection.stream()
                            .map(p -> p.split(":"))
                            .map(StringUtils::stripAll)
                            .map(arr -> String.join(":", arr))
                            .flatMap(pattern -> dependencies.stream()
                                    .filter(artifact -> compareDependency(versionParser, pattern, artifact)))
                            .collect(Collectors.toSet()))
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            if (e.getCause() instanceof VersionParserException) {
                throw new EnforcerRuleException(e.getMessage());
            }
            throw e;
        }
    }

    public static Predicate<Artifact> prepareDependencyArtifactMatcher(
            VersionParser versionParser, Collection<String> patterns) {
        return cleansePatterns(patterns)
                .map(p -> new ArtifactMatcher.Pattern(versionParser, p))
                .map(pattern -> (Predicate<Artifact>) pattern::match)
                .reduce(Predicate::or)
                .orElse(test -> false);
    }

    public static Predicate<org.apache.maven.api.model.Plugin> preparePluginMatcher(
            VersionParser versionParser, Collection<String> patterns) {
        return cleansePatterns(patterns)
                .map(p -> new ArtifactMatcher.Pattern(versionParser, p))
                .map(pattern -> (Predicate<org.apache.maven.api.model.Plugin>) pattern::match)
                .reduce(Predicate::or)
                .orElse(test -> false);
    }

    private static Stream<String> cleansePatterns(Collection<String> patterns) {
        return ofNullable(patterns)
                .map(collection -> collection.stream()
                        .map(p -> p.split(":"))
                        .map(StringUtils::stripAll)
                        .map(arr -> String.join(":", arr)))
                .orElse(Stream.empty());
    }

    public static boolean compareDependency(VersionParser versionParser, String pattern, Artifact artifact) {
        return new ArtifactMatcher.Pattern(versionParser, pattern).match(artifact);
    }
}
