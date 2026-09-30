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

import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;

class BanDependencyManagementScopeTest {
    @Test
    void testGetViolatingDependencies() {
        BanDependencyManagementScope rule = new BanDependencyManagementScope(
                mock(Project.class), mock(ModelXmlFactory.class), EnforcerTestUtils.getVersionParser());

        Dependency depWithoutScope = createDependency("myGroup", "artifact1", null);
        Dependency depWithScope = createDependency("myGroup", "artifact2", "1.1.0", "provided");
        Dependency depWithImportScope = createDependency("myGroup", "artifact3", "1.1.0", "import");
        Dependency excludedDepWithScope = createDependency("myGroup", "artifact4", "1.1.0", "provided");
        DependencyManagement depMgmt = DependencyManagement.newBuilder()
                .dependencies(Arrays.asList(depWithoutScope, depWithScope, depWithImportScope, excludedDepWithScope))
                .build();
        rule.setExcludes(Collections.singletonList("*:artifact4"));
        rule.setLog(mock(EnforcerLogger.class));
        assertIterableEquals(Collections.singletonList(depWithScope), rule.getViolatingDependencies(depMgmt));
    }

    static Dependency createDependency(String groupId, String artifactId, String version) {
        return createDependency(groupId, artifactId, version, null);
    }

    static Dependency createDependency(String groupId, String artifactId, String version, String scope) {
        Dependency.Builder dependency = Dependency.newBuilder().groupId(groupId).artifactId(artifactId);
        if (version != null) {
            dependency.version(version);
        }
        if (scope != null) {
            dependency.scope(scope);
        }
        return dependency.build();
    }
}
