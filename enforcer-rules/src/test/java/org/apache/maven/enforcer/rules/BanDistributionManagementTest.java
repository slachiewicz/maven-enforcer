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

import java.util.Optional;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.DeploymentRepository;
import org.apache.maven.api.model.DistributionManagement;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class is intended to test the {@link BanDistributionManagement} rule.
 *
 * @author <a href="mailto:khmarbaise@apache.org">Karl Heinz Marbaise</a>
 */
@ExtendWith(MockitoExtension.class)
class BanDistributionManagementTest {

    private Project project;

    @Mock
    private EnforcerLogger log;

    private ModelXmlFactory modelXmlFactory;

    private BanDistributionManagement rule;

    @BeforeEach
    void setup() {
        project = EnforcerTestUtils.mockProject();
        modelXmlFactory = mock(ModelXmlFactory.class);
        rule = new BanDistributionManagement(project, modelXmlFactory);
        rule.setLog(log);
    }

    @Test
    void shouldNotFailWithoutDistributionManagement() throws Exception {
        setupProjectWithoutDistributionManagement();
        rule.execute();
        // intentionally no assert cause in case of an exception the test will be red.
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;repository&gt;
     *    ...
     *   &lt;/repository&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     */
    @Test
    void shouldThrowExceptionIfDistributionManagementIsDefinedWithRepository() {
        setupProjectWithDistributionManagement(DeploymentRepository.newInstance(), null);
        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;snapshotRepository&gt;
     *    ...
     *   &lt;/snapshotRepository&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     */
    @Test
    void shouldThrowExceptionIfDistributionManagementIsDefinedWithRepositorySnapshotRepository() {
        setupProjectWithDistributionManagement(null, DeploymentRepository.newInstance());

        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;repository&gt;
     *    ...
     *   &lt;/repository&gt;
     *   &lt;snapshotRepository&gt;
     *    ...
     *   &lt;/snapshotRepository&gt;
     *   &lt;site&gt;
     *    ...
     *   &lt;/site&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     */
    @Test
    void shouldThrowExceptionIfDistributionManagementIsDefinedWithRepositorySnapshotRepositorySite() {
        setupProjectWithDistributionManagement(DeploymentRepository.newInstance(), null);

        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;repository&gt;
     *    ...
     *   &lt;/repository&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     *
     * @throws Exception if any occurs
     */
    @Test
    void shouldAllowDistributionManagementHavingRepository() throws Exception {
        setupProjectWithDistributionManagement(null, null);
        rule.setAllowRepository(true);
        rule.execute();
        // intentionally no assert cause in case of an exception the test will be red.
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;repository&gt;
     *    ...
     *   &lt;/repository&gt;
     *   &lt;snapshotRepository&gt;
     *    ...
     *   &lt;/snapshotRepository&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     *
     * @throws Exception if any occurs
     */
    @Test
    void shouldAllowDistributionManagementHavingRepositorySnapshotRepository() throws Exception {
        setupProjectWithDistributionManagement(null, null);

        rule.setAllowRepository(true);
        rule.setAllowSnapshotRepository(true);
        rule.execute();
        // intentionally no assert cause in case of an exception the test will be red.
    }

    /**
     * <pre>
     * &lt;distributionManagement&gt;
     *   &lt;repository&gt;
     *    ...
     *   &lt;/repository&gt;
     *   &lt;snapshotRepository&gt;
     *    ...
     *   &lt;/snapshotRepository&gt;
     *   &lt;site&gt;
     *    ...
     *   &lt;/site&gt;
     * &lt;/distributionManagement&gt;
     * </pre>
     *
     * @throws Exception if any occurs
     */
    @Test
    void shouldAllowDistributionManagementHavingRepositorySnapshotRepositorySite() throws Exception {
        setupProjectWithDistributionManagement(null, null);
        rule.setAllowRepository(true);
        rule.setAllowSnapshotRepository(true);
        rule.setAllowSite(true);
        rule.execute();
        // intentionally no assert cause in case of an exception the test will be red.
    }

    @Test
    void shouldOutputCustomMessageWhenBanned() {
        String customMessage = "Custom distribution management message";
        rule.setMessage(customMessage);
        setupProjectWithDistributionManagement(DeploymentRepository.newInstance(), null);

        assertThatThrownBy(() -> rule.execute())
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageStartingWith(customMessage)
                .hasMessageContaining("repository in distributionManagement");
    }

    private void setupProjectWithoutDistributionManagement() {
        setupProject(null);
    }

    private void setupProjectWithDistributionManagement(
            DeploymentRepository repository, DeploymentRepository snapshotRepository) {
        DistributionManagement dm = DistributionManagement.newBuilder()
                .repository(repository)
                .snapshotRepository(snapshotRepository)
                .build();
        setupProject(dm);

        when(project.getParent()).thenReturn(Optional.of(mock(Project.class)));
        when(project.isTopProject()).thenReturn(true);
    }

    private void setupProject(DistributionManagement distributionManagement) {
        Model model = Model.newBuilder()
                .distributionManagement(distributionManagement)
                .build();
        when(modelXmlFactory.read(any(XmlReaderRequest.class))).thenReturn(model);
    }
}
