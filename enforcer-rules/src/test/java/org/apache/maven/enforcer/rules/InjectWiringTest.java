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
import java.util.List;

import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.LifecycleRegistry;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.di.Injector;
import org.apache.maven.di.Key;
import org.apache.maven.enforcer.rule.api.EnforcerRuleBase;
import org.apache.maven.enforcer.rules.checksum.RequireFileChecksum;
import org.apache.maven.enforcer.rules.checksum.RequireTextFileChecksum;
import org.apache.maven.enforcer.rules.dependency.BanTransitiveDependencies;
import org.apache.maven.enforcer.rules.dependency.BannedDependencies;
import org.apache.maven.enforcer.rules.dependency.DependencyConvergence;
import org.apache.maven.enforcer.rules.dependency.EnforceBytecodeVersion;
import org.apache.maven.enforcer.rules.dependency.RequireReleaseDeps;
import org.apache.maven.enforcer.rules.files.RequireFilesDontExist;
import org.apache.maven.enforcer.rules.files.RequireFilesExist;
import org.apache.maven.enforcer.rules.files.RequireFilesSize;
import org.apache.maven.enforcer.rules.property.RequireEnvironmentVariable;
import org.apache.maven.enforcer.rules.property.RequireProperty;
import org.apache.maven.enforcer.rules.version.RequireJavaVersion;
import org.apache.maven.enforcer.rules.version.RequireMavenVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The rules are created by the injector of Maven 4, with the services of its implementation.
 */
class InjectWiringTest {

    static List<Class<? extends EnforcerRuleBase>> rules() {
        return Arrays.asList(
                AlwaysFail.class,
                AlwaysPass.class,
                BanDependencyManagementScope.class,
                BanDistributionManagement.class,
                BanDuplicatePomDependencyVersions.class,
                BannedPlugins.class,
                BannedRepositories.class,
                EvaluateBeanshell.class,
                ExternalRules.class,
                ReactorModuleConvergence.class,
                RequireActiveProfile.class,
                RequireExplicitDependencyScope.class,
                RequireJavaVendor.class,
                RequireMatchingCoordinates.class,
                RequireNoRepositories.class,
                RequireOS.class,
                RequirePluginVersions.class,
                RequirePrerequisite.class,
                RequireReleaseVersion.class,
                RequireSameVersions.class,
                RequireSnapshotVersion.class,
                RequireFileChecksum.class,
                RequireTextFileChecksum.class,
                BanTransitiveDependencies.class,
                BannedDependencies.class,
                DependencyConvergence.class,
                EnforceBytecodeVersion.class,
                RequireReleaseDeps.class,
                RequireFilesDontExist.class,
                RequireFilesExist.class,
                RequireFilesSize.class,
                RequireEnvironmentVariable.class,
                RequireProperty.class,
                RequireJavaVersion.class,
                RequireMavenVersion.class);
    }

    private Injector injector() {
        Injector injector = Injector.create();
        injector.discover(getClass().getClassLoader());
        injector.bindInstance(Session.class, EnforcerTestUtils.getRealSession());
        injector.bindInstance(Project.class, mock(Project.class));
        injector.bindInstance(MojoExecution.class, mock(MojoExecution.class));
        // provided by the core of Maven, not by its implementation library
        injector.bindInstance(LifecycleRegistry.class, mock(LifecycleRegistry.class));
        injector.bindInstance(ProjectManager.class, mock(ProjectManager.class));
        return injector;
    }

    @Test
    void ruleIsFoundByNameAsEnforcerRule() {
        EnforcerRuleBase rule = injector().getInstance(Key.of(EnforcerRuleBase.class, "requireOS"));

        assertThat(rule).isInstanceOf(RequireOS.class);
    }

    @ParameterizedTest
    @MethodSource("rules")
    void ruleIsCreatedByInjector(Class<? extends EnforcerRuleBase> ruleClass) {
        EnforcerRuleBase rule = injector().getInstance(Key.of(ruleClass));

        assertThat(rule).isInstanceOf(ruleClass);
    }
}
