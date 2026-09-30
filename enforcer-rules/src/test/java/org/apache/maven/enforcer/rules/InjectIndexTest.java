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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.di.Named;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The index of the components is maintained by hand, because the parent POM disables annotation processing. A
 * component which is not in the index is not found: there is "No binding" for it.
 */
class InjectIndexTest {

    private static final String INDEX = "META-INF/maven/org.apache.maven.api.di.Inject";

    private Set<String> readIndex() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(INDEX)) {
            assertThat(in).as(INDEX).isNotNull();
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private Set<String> findNamedClasses() throws Exception {
        Path classes = Paths.get(AlwaysPass.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).collect(Collectors.toList())) {
                String name = classes.relativize(file)
                        .toString()
                        .replace(file.getFileSystem().getSeparator(), ".");
                name = name.substring(0, name.length() - ".class".length());
                if (Class.forName(name).isAnnotationPresent(Named.class)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    @Test
    void everyNamedClassIsInTheIndex() throws Exception {
        assertThat(readIndex()).containsExactlyInAnyOrderElementsOf(findNamedClasses());
    }
}
