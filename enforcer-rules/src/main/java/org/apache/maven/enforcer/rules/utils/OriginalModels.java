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

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;

/**
 * Reads the original model of a project, the one without inheritance and interpolation applied.
 * <p>
 * The Maven 4 API has no equivalent of {@code MavenProject.getOriginalModel()}, so the {@code pom.xml} file of
 * the project is parsed again, including the input locations.
 *
 * @since 3.7.0
 */
public final class OriginalModels {

    private OriginalModels() {}

    /**
     * Reads the original model of the project.
     *
     * @param modelXmlFactory the factory used to parse the {@code pom.xml}
     * @param project the project
     * @return the model as written in the {@code pom.xml} file
     * @throws EnforcerRuleError if the file cannot be read
     */
    public static Model read(ModelXmlFactory modelXmlFactory, Project project) throws EnforcerRuleError {
        try {
            return modelXmlFactory.read(XmlReaderRequest.builder()
                    .path(project.getPomPath())
                    .strict(false)
                    .addLocationInformation(true)
                    .modelId(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion())
                    .build());
        } catch (XmlReaderException e) {
            throw new EnforcerRuleError("Unable to read the model of project " + project.getId(), e);
        }
    }
}
