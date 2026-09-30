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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.api.Node;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.InputLocation;
import org.apache.maven.api.model.InputSource;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.apache.maven.impl.standalone.ApiRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * The Class EnforcerTestUtils.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public final class EnforcerTestUtils {

    private static Session session;

    private EnforcerTestUtils() {}

    /**
     * Gets a real session with the services of the Maven 4 implementation, for example to parse versions.
     *
     * @return the session
     */
    public static synchronized Session getRealSession() {
        if (session == null) {
            session = ApiRunner.createSession();
        }
        return session;
    }

    /**
     * Gets the real parser of versions.
     *
     * @return the version parser
     */
    public static VersionParser getVersionParser() {
        return getRealSession().getService(VersionParser.class);
    }

    /**
     * New plugin.
     *
     * @param groupId the group id
     * @param artifactId the artifact id
     * @param version the version
     * @return the plugin
     */
    public static Plugin newPlugin(String groupId, String artifactId, String version) {
        InputSource inputSource = new InputSource("unit", null);
        return Plugin.newBuilder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .location("version", InputLocation.of(0, 0, inputSource))
                .build();
    }

    /**
     * A factory which reads always the given model, as the original model of any project.
     *
     * @param model the model
     * @return the factory
     */
    public static ModelXmlFactory modelXmlFactory(Model model) {
        ModelXmlFactory factory = mock(ModelXmlFactory.class);
        lenient().when(factory.read(any(XmlReaderRequest.class))).thenReturn(model);
        return factory;
    }

    /**
     * A project with a pom file, which can be read by the factory of models.
     *
     * @return the project
     */
    public static Project mockProject() {
        Project project = mock(Project.class);
        Path pomPath = Paths.get("pom.xml");
        lenient().when(project.getPomPath()).thenReturn(pomPath);
        return project;
    }

    public static Node getDependencyNodeWithMultipleSnapshots() {
        return new DependencyNodeBuilder()
                .withType(DependencyNodeBuilder.NodeType.POM)
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childA")
                        .withVersion("1.0.0")
                        .withChildNode(new DependencyNodeBuilder()
                                .withArtifactId("childAA")
                                .withVersion("1.0.0-SNAPSHOT")
                                .build())
                        .build())
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childB")
                        .withVersion("2.0.0-SNAPSHOT")
                        .build())
                .build();
    }

    public static Node getDependencyNodeWithOptionalSnapshot() {
        return new DependencyNodeBuilder()
                .withType(DependencyNodeBuilder.NodeType.POM)
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childA")
                        .withVersion("1.0.0")
                        .withChildNode(new DependencyNodeBuilder()
                                .withArtifactId("optional-childA-snapshot")
                                .withOptional(true)
                                .withVersion("1.0.0-SNAPSHOT")
                                .build())
                        .build())
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childB")
                        .withVersion("2.0.0-SNAPSHOT")
                        .withOptional(true)
                        .build())
                .build();
    }

    public static Node getDependencyNodeWithMultipleTestSnapshots() {
        return new DependencyNodeBuilder()
                .withType(DependencyNodeBuilder.NodeType.POM)
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childA")
                        .withVersion("1.0.0")
                        .withChildNode(new DependencyNodeBuilder()
                                .withArtifactId("childAA")
                                .withVersion("1.0.0-SNAPSHOT")
                                .withScope("test")
                                .build())
                        .build())
                .withChildNode(new DependencyNodeBuilder()
                        .withArtifactId("childB")
                        .withVersion("2.0.0-SNAPSHOT")
                        .withScope("test")
                        .build())
                .build();
    }
}
