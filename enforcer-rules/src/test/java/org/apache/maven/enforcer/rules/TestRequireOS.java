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

import org.apache.maven.api.Session;
import org.apache.maven.api.services.OsService;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.impl.model.DefaultOsService;
import org.apache.maven.impl.model.profile.OperatingSystemProfileActivator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class TestRequireOS {

    private Session session;

    private OsService osService;

    @BeforeEach
    void setup() {
        session = mock(Session.class);
        osService = new DefaultOsService();
    }

    private RequireOS createRule() {
        return new RequireOS(new OperatingSystemProfileActivator(), session, osService);
    }

    @Test
    void testOS() {
        RequireOS rule = createRule();

        // the current family is valid, the other is not: unix is not a Windows family and Windows is not unix
        String validFamily = osService.family();
        String invalidFamily = osService.isWindows() ? "unix" : "windows";

        rule.setFamily(validFamily);
        assertThat(rule.isAllowed()).isTrue();

        rule.setFamily(invalidFamily);
        assertThat(rule.isAllowed()).isFalse();

        rule.setFamily("!" + invalidFamily);
        assertThat(rule.isAllowed()).isTrue();

        rule.setFamily(null);
        rule.setArch(osService.arch());
        assertThat(rule.isAllowed()).isTrue();

        rule.setArch("somecrazyarch");
        assertThat(rule.isAllowed()).isFalse();

        rule.setArch("!somecrazyarch");
        assertThat(rule.isAllowed()).isTrue();

        rule.setArch(null);

        rule.setName(osService.name());
        assertThat(rule.isAllowed()).isTrue();

        rule.setName("somecrazyname");
        assertThat(rule.isAllowed()).isFalse();

        rule.setName("!somecrazyname");
        assertThat(rule.isAllowed()).isTrue();

        rule.setName(null);

        rule.setVersion(osService.version());
        assertThat(rule.isAllowed()).isTrue();

        rule.setVersion("somecrazyversion");
        assertThat(rule.isAllowed()).isFalse();

        rule.setVersion("!somecrazyversion");
        assertThat(rule.isAllowed()).isTrue();
    }

    @Test
    void testInvalidFamily() {
        RequireOS rule = createRule();
        rule.setLog(mock(EnforcerLogger.class));

        rule.setFamily("junk");
        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleError.class)
                .hasMessageStartingWith("Invalid Family type used. Valid family types are: ");
    }

    @Test
    void testId() {
        RequireOS rule = createRule();
        rule.setVersion("1.2");
        assertThat(rule.getCacheId()).isNotEmpty();
    }
}
