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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Language;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.PathType;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Type;
import org.apache.maven.api.Version;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;

import static java.util.Optional.ofNullable;

/**
 * Builds the nodes of the dependency tree. The Maven 4 API has no public implementation of the nodes.
 */
public class DependencyNodeBuilder {

    public enum NodeType {
        WAR("war"),
        JAR("jar"),
        POM("pom");

        private final String id;

        NodeType(String id) {
            this.id = id;
        }

        String asString() {
            return id;
        }
    }

    private String groupId;

    private String artifactId;

    private String version;

    private NodeType type;

    private String scope;

    private boolean optional;

    private String classifier = "classifier";

    private final List<Node> children = new ArrayList<>();

    public DependencyNodeBuilder withGroupId(String val) {
        groupId = val;
        return this;
    }

    public DependencyNodeBuilder withArtifactId(String val) {
        artifactId = val;
        return this;
    }

    public DependencyNodeBuilder withVersion(String val) {
        version = val;
        return this;
    }

    public DependencyNodeBuilder withType(NodeType val) {
        type = val;
        return this;
    }

    public DependencyNodeBuilder withScope(String val) {
        scope = val;
        return this;
    }

    public DependencyNodeBuilder withOptional(boolean val) {
        optional = val;
        return this;
    }

    public DependencyNodeBuilder withClassifier(String val) {
        classifier = val;
        return this;
    }

    public DependencyNodeBuilder withChildNode(Node val) {
        children.add(val);
        return this;
    }

    public Node build() {
        StubDependency dependency = new StubDependency(
                ofNullable(groupId).orElse("default-group"),
                ofNullable(artifactId).orElse("default-artifact"),
                ofNullable(version).orElse("default-version"),
                classifier,
                ofNullable(type).map(NodeType::asString).orElse("jar"),
                ofNullable(scope).orElse("compile"),
                optional);
        return new StubNode(dependency, children);
    }

    /**
     * Creates a dependency of a node.
     */
    public static Dependency dependency(
            String groupId, String artifactId, String version, String scope, String type, String classifier) {
        return new StubDependency(groupId, artifactId, version, classifier, type, scope, false);
    }

    /**
     * Creates a root node with the dependencies as its children.
     */
    public static Node rootOf(List<Dependency> dependencies) {
        List<Node> children = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            children.add(node(dependency));
        }
        return new StubNode(dependency("default-group", "root", "1.0", "compile", "pom", ""), children);
    }

    public static Node node(Dependency dependency) {
        return new StubNode(dependency, Collections.emptyList());
    }

    private static final class StubType implements Type {
        private final String id;

        StubType(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Language getLanguage() {
            return null;
        }

        @Override
        public String getExtension() {
            return id;
        }

        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public boolean isIncludesDependencies() {
            return false;
        }

        @Override
        public java.util.Set<PathType> getPathTypes() {
            return Collections.emptySet();
        }
    }

    private static final class StubDependency implements Dependency {
        private final String groupId;
        private final String artifactId;
        private final Version version;
        private final String classifier;
        private final Type type;
        private final DependencyScope scope;
        private final boolean optional;

        StubDependency(
                String groupId,
                String artifactId,
                String version,
                String classifier,
                String type,
                String scope,
                boolean optional) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = EnforcerTestUtils.getVersionParser().parseVersion(version);
            this.classifier = classifier;
            this.type = new StubType(type);
            this.scope = DependencyScope.forId(scope);
            this.optional = optional;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public DependencyScope getScope() {
            return scope;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public DependencyCoordinates toCoordinates() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Version getBaseVersion() {
            return version;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public String getExtension() {
            return type.getExtension();
        }

        @Override
        public boolean isSnapshot() {
            return EnforcerTestUtils.getVersionParser().isSnapshot(version.toString());
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + type.id() + ":" + version + ":" + scope.id();
        }
    }

    private static final class StubNode implements Node {
        private final Dependency dependency;
        private final List<Node> children;

        StubNode(Dependency dependency, List<Node> children) {
            this.dependency = dependency;
            this.children = new ArrayList<>(children);
        }

        @Override
        public Artifact getArtifact() {
            return dependency;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }

        @Override
        public List<Node> getChildren() {
            return children;
        }

        @Override
        public List<RemoteRepository> getRemoteRepositories() {
            return Collections.emptyList();
        }

        @Override
        public Optional<RemoteRepository> getRepository() {
            return Optional.empty();
        }

        @Override
        public boolean accept(NodeVisitor visitor) {
            if (visitor.enter(this)) {
                for (Node child : children) {
                    if (!child.accept(visitor)) {
                        break;
                    }
                }
            }
            return visitor.leave(this);
        }

        @Override
        public Node filter(Predicate<Node> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String asString() {
            return dependency.toString();
        }

        @Override
        public String toString() {
            return asString();
        }
    }
}
