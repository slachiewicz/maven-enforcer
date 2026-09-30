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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests reading of the original model with the real parser of the Maven 4 implementation.
 */
class OriginalModelsTest {

    @TempDir
    private Path temporaryFolder;

    private Project project(Path pom) {
        Project project = mock(Project.class);
        when(project.getPomPath()).thenReturn(pom);
        when(project.getGroupId()).thenReturn("org.example");
        when(project.getArtifactId()).thenReturn("child");
        when(project.getVersion()).thenReturn("1.0");
        return project;
    }

    private ModelXmlFactory factory() {
        return EnforcerTestUtils.getRealSession().getService(ModelXmlFactory.class);
    }

    @Test
    void shouldReadModelWithoutInheritanceAndWithLocations() throws Exception {
        Path pom = temporaryFolder.resolve("pom.xml");
        Files.write(
                pom,
                ("<project>\n"
                                + "  <modelVersion>4.0.0</modelVersion>\n"
                                + "  <parent><groupId>org.example</groupId><artifactId>parent</artifactId>"
                                + "<version>1.0</version></parent>\n"
                                + "  <artifactId>child</artifactId>\n"
                                + "  <dependencies>\n"
                                + "    <dependency>\n"
                                + "      <groupId>g</groupId><artifactId>a</artifactId><version>1</version>\n"
                                + "    </dependency>\n"
                                + "    <dependency>\n"
                                + "      <groupId>g</groupId><artifactId>b</artifactId><version>1</version>"
                                + "<scope>test</scope>\n"
                                + "    </dependency>\n"
                                + "  </dependencies>\n"
                                + "</project>\n")
                        .getBytes(StandardCharsets.UTF_8));

        Model model = OriginalModels.read(factory(), project(pom));

        // nothing is inherited from the parent
        assertThat(model.getGroupId()).isNull();
        assertThat(model.getDependencies()).hasSize(2);

        // the scope which is not in the file is not defaulted
        Dependency withoutScope = model.getDependencies().get(0);
        Dependency withScope = model.getDependencies().get(1);
        assertThat(withoutScope.getScope()).isNull();
        assertThat(withScope.getScope()).isEqualTo("test");

        // the location of the dependency in the file
        assertThat(withoutScope.getLocation("").getLineNumber()).isEqualTo(6);
        assertThat(withoutScope.getLocation("").getSource().getModelId()).isEqualTo("org.example:child:1.0");
        // the source is an URI of the file
        assertThat(withoutScope.getLocation("").getSource().getLocation())
                .isEqualTo(pom.toUri().toString());
    }

    @Test
    void shouldFailWhenFileIsNotReadable() throws IOException {
        Path pom = temporaryFolder.resolve("missing.xml");

        assertThatThrownBy(() -> OriginalModels.read(factory(), project(pom))).isInstanceOf(EnforcerRuleError.class);
    }
}
