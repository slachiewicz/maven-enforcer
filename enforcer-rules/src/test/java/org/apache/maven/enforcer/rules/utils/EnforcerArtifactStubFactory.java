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
import java.util.List;

import org.apache.maven.api.Dependency;

/**
 * Creates the dependencies used in tests.
 */
public class EnforcerArtifactStubFactory {

    public Dependency getReleaseArtifact() {
        return createArtifact("testGroupId", "release", "1.0");
    }

    public Dependency getSnapshotArtifact() {
        return createArtifact("testGroupId", "snapshot", "2.0-SNAPSHOT");
    }

    public List<Dependency> getScopedArtifacts() {
        List<Dependency> list = new ArrayList<>();
        list.add(createArtifact("g", "compile", "1.0", "compile", "jar", ""));
        list.add(createArtifact("g", "provided", "1.0", "provided", "jar", ""));
        list.add(createArtifact("g", "test", "1.0", "test", "jar", ""));
        list.add(createArtifact("g", "runtime", "1.0", "runtime", "jar", ""));
        list.add(createArtifact("g", "system", "1.0", "system", "jar", ""));
        return list;
    }

    public List<Dependency> getTypedArtifacts() {
        List<Dependency> list = new ArrayList<>();
        list.add(createArtifact("g", "a", "1.0", "compile", "war", null));
        list.add(createArtifact("g", "b", "1.0", "compile", "jar", null));
        list.add(createArtifact("g", "c", "1.0", "compile", "sources", null));
        list.add(createArtifact("g", "d", "1.0", "compile", "zip", null));
        list.add(createArtifact("g", "e", "1.0", "compile", "rar", null));
        return list;
    }

    public Dependency createArtifact(String groupId, String artifactId, String version) {
        return createArtifact(groupId, artifactId, version, "compile", "jar", "");
    }

    private Dependency createArtifact(
            String groupId, String artifactId, String version, String scope, String type, String classifier) {
        return DependencyNodeBuilder.dependency(groupId, artifactId, version, scope, type, classifier);
    }
}
