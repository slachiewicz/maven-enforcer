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
package org.apache.maven.enforcer.rules.dependency;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Node;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.EnforcerTestUtils;
import org.apache.maven.enforcer.rules.utils.DependencyNodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the execution of the rule with the dependencies resolved to files.
 */
class EnforceBytecodeVersionRuleTest {

    @TempDir
    private Path temporaryFolder;

    private Session session;

    private ResolverUtil resolverUtil;

    private EnforceBytecodeVersion rule;

    private DependencyResolverResult result;

    private final Map<Dependency, Path> files = new HashMap<>();

    private final Map<Object, Object> sessionData = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        session = mock(Session.class);
        resolverUtil = mock(ResolverUtil.class);
        result = mock(DependencyResolverResult.class);
        when(result.getDependencies()).thenReturn(files);

        SessionData data = mock(SessionData.class);
        when(data.computeIfAbsent(any(), any()))
                .thenAnswer(invocation -> sessionData.computeIfAbsent(
                        invocation.getArgument(0), k -> ((Supplier<Object>) invocation.getArgument(1)).get()));
        when(session.getData()).thenReturn(data);

        when(resolverUtil.resolveDependencies(anyBoolean(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(result);

        rule = new EnforceBytecodeVersion(
                session, mock(Project.class), resolverUtil, EnforcerTestUtils.getVersionParser());
        rule.setLog(mock(EnforcerLogger.class));
    }

    private void setUp2() throws Exception {
        rule = new EnforceBytecodeVersion(
                session, mock(Project.class), resolverUtil, EnforcerTestUtils.getVersionParser());
        rule.setLog(mock(EnforcerLogger.class));
        set("maxJdkVersion", "11");
    }

    private void set(String fieldName, Object value) throws ReflectiveOperationException {
        Field field = EnforceBytecodeVersion.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(rule, value);
    }

    /**
     * Creates a jar with a class file with the given major version.
     */
    private Path createJar(String name, int major) throws IOException {
        Path jar = temporaryFolder.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jarOut = new JarOutputStream(out)) {
            jarOut.putNextEntry(new JarEntry("org/example/Example.class"));
            jarOut.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, (byte) major});
            jarOut.closeEntry();
        }
        return jar;
    }

    private Node tree(String artifactId, String version, Path file) {
        Node node = new DependencyNodeBuilder()
                .withArtifactId(artifactId)
                .withVersion(version)
                .build();
        files.put(node.getDependency(), file);
        when(result.getRoot())
                .thenReturn(new DependencyNodeBuilder()
                        .withChildNode(node)
                        .withType(DependencyNodeBuilder.NodeType.POM)
                        .build());
        return node;
    }

    @Test
    void shouldFailWhenBytecodeIsNewerThanAllowed() throws Exception {
        tree("new-one", "1.0", createJar("new-one", 61));
        set("maxJdkVersion", "11");

        assertThatCode(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("Found Banned Dependency: default-group:new-one:jar:classifier:1.0");
    }

    @Test
    void shouldPassWhenBytecodeIsNotNewerThanAllowed() throws Exception {
        tree("old-one", "1.0", createJar("old-one", 55));
        set("maxJdkVersion", "11");

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipFilesWhichAreNotJars() throws Exception {
        Path pom = Files.write(temporaryFolder.resolve("a.pom"), new byte[0]);
        tree("a-pom", "1.0", pom);
        set("maxJdkVersion", "11");

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldCheckFileOncePerSession() throws Exception {
        tree("new-one", "1.0", createJar("new-one", 61));
        set("maxJdkVersion", "11");

        assertThatCode(rule::execute).isInstanceOf(EnforcerRuleException.class);

        // a rule of another module in the same session, the same file with the same parameters is already checked
        setUp2();
        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipExcludedDependency() throws Exception {
        tree("new-one", "1.0", createJar("new-one", 61));
        set("maxJdkVersion", "11");
        set("excludes", Collections.singletonList("default-group:new-one"));

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }

    @Test
    void shouldCheckOnlyDirectDependenciesWhenTransitiveIsNotSearched() throws Exception {
        Path jar = createJar("deep", 61);
        Node deep = new DependencyNodeBuilder().withArtifactId("deep").build();
        files.put(deep.getDependency(), jar);
        Node direct = new DependencyNodeBuilder()
                .withArtifactId("direct")
                .withChildNode(deep)
                .build();
        when(result.getRoot())
                .thenReturn(new DependencyNodeBuilder().withChildNode(direct).build());
        set("maxJdkVersion", "11");
        set("searchTransitive", false);

        assertThatCode(rule::execute).doesNotThrowAnyException();
    }
}
