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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.AbstractStandardEnforcerRule;
import org.apache.maven.enforcer.rules.utils.ArtifactMatcher;
import org.apache.maven.enforcer.rules.utils.ArtifactUtils;

import static java.util.Objects.requireNonNull;

/**
 * Enforcer rule that will check the bytecode version of each class of each dependency.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Java_class_file#General_layout">Java class file general layout</a>
 * @since 3.6.3
 */
@Priority(10) // if both mojohaus and this one are present, this prevails
@Named("enforceBytecodeVersion")
public class EnforceBytecodeVersion extends AbstractStandardEnforcerRule {
    /**
     * Default ignores when validating against jdk < 9 because <code>module-info.class</code> will always have level 1.9.
     */
    private static final String[] DEFAULT_CLASSES_IGNORE_BEFORE_JDK_9 = {"module-info"};

    private static final Pattern MULTI_RELEASE = Pattern.compile("META-INF/versions/(\\d+)/.*");

    private static final Map<String, Integer> JDK_TO_MAJOR_VERSION_NUMBER_MAPPING = new LinkedHashMap<>();

    static {
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.1", 45);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.2", 46);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.3", 47);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.4", 48);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.5", 49);
        // Java6
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.6", 50);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("6", 50);
        // Java7
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.7", 51);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("7", 51);
        // Java8
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("8", 52);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.8", 52);
        // Java9
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("9", 53);
        JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.put("1.9", 53);
    }

    /**
     * Visible for testing.
     */
    static String renderVersion(int major, int minor) {
        if (minor == 0) {
            if (major > 52) {
                return "JDK " + (major - 44);
            }
            for (Map.Entry<String, Integer> entry : JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.entrySet()) {
                if (major == entry.getValue()) {
                    return "JDK " + entry.getKey();
                }
            }
        }
        return major + "." + minor;
    }

    /**
     * Visible for testing.
     */
    static Integer decodeMajorVersion(String version) {
        Integer major = JDK_TO_MAJOR_VERSION_NUMBER_MAPPING.get(version);
        if (major == null) {
            try {
                major = Integer.parseInt(version);
                major = major < 8 ? null : major + 44;
            } catch (NumberFormatException e) {
                // ignore, will return null
            }
        }
        return major;
    }

    /**
     * Custom message, optional. If configured, will be present on first line of the error message.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final SessionData.Key<ConcurrentMap<ChecksOptions, Boolean>> PERFORMED_CHECKS =
            (SessionData.Key) SessionData.key(ConcurrentMap.class, EnforceBytecodeVersion.class);

    private String message;

    /**
     * JDK version as used for example in the maven-compiler-plugin: 8, 11 and so on. If in need of more precise
     * configuration please see {@link #maxJavaMajorVersionNumber} and {@link #maxJavaMinorVersionNumber} Mandatory if
     * {@link #maxJavaMajorVersionNumber} not specified.
     */
    private String maxJdkVersion;

    /**
     * If unsure, don't use that parameter. Better look at {@link #maxJdkVersion}. Mandatory if {@link #maxJdkVersion}
     * is not specified. see http://en.wikipedia.org/wiki/Java_class_file#General_layout
     */
    private int maxJavaMajorVersionNumber = -1;

    /**
     * This parameter is here for potentially advanced use cases, but it seems like it is actually always 0.
     *
     * @see #maxJavaMajorVersionNumber
     * @see <a href="https://en.wikipedia.org/wiki/Java_class_file#General_layout">Java class file general layout</a>
     */
    private int maxJavaMinorVersionNumber = 0;

    /**
     * Specify the excluded dependencies. This can be a list of artifacts in the format
     * <code>groupId[:artifactId][:version]</code>. Any of the sections can be a wildcard
     * by using '*' (ie group:*:1.0) <br>
     */
    private List<String> excludes = null;

    /**
     * Specify the included dependencies. This can be a list of artifacts in the format
     * <code>groupId[:artifactId][:version]</code>. Any of the sections can be a wildcard
     * by using '*' (ie group:*:1.0) <br>
     * Includes override the exclude rules. It is meant to allow wide exclusion rules
     * with wildcards and still allow a
     * smaller set of includes. <br>
     * For example, to ban all xerces except xerces-api -&gt; exclude "xerces", include "xerces:xerces-api"
     */
    private List<String> includes = null;

    /**
     * List of classes to fully ignore, interpreted as regular expression.
     */
    private List<String> ignoreClasses;

    /**
     * List of dependency scopes, to include dependencies in it. Default is to include all scopes.
     */
    private List<String> scopes;

    /**
     * List of dependency scopes, to ignore dependencies in it. Default is to not exclude any scope. Excluding
     * scopes like {@code ["test", "provided"]} is usually right thing to do.
     */
    private List<String> ignoredScopes;

    /**
     * If set, will ignore optional dependencies of the project.
     */
    private boolean ignoreOptionals = false;

    /**
     * If set, transitive hull of project is being processed, otherwise only direct dependencies.
     * Default is {@code true}.
     */
    private boolean searchTransitive = true;

    /**
     * Process module-info and Multi-Release JAR classes if {@code true}.
     */
    private boolean strict = false;

    /**
     * Optimization to calculate same JAR with same options once per session, by default is enabled.
     */
    private boolean processOncePerSession = true;

    // ---

    /**
     * Internal: strings (regexp expressions) are collected here to ignore matched classes.
     */
    private final List<String> ignorableClassesPatterns = new ArrayList<>();

    private final Session session;

    private final Project project;

    private final ResolverUtil resolverUtil;

    private final VersionParser versionParser;

    @Inject
    EnforceBytecodeVersion(Session session, Project project, ResolverUtil resolverUtil, VersionParser versionParser) {
        this.session = requireNonNull(session);
        this.project = requireNonNull(project);
        this.resolverUtil = requireNonNull(resolverUtil);
        this.versionParser = requireNonNull(versionParser);
    }

    @Override
    public void execute() throws EnforcerRuleException {
        validateAndPopulateParameters();

        DependencyResolverResult resolved = resolverUtil.resolveDependencies(
                false, true, ignoreOptionals, ignoredScopes == null ? Collections.emptyList() : ignoredScopes);
        Map<Dependency, Path> files = resolved.getDependencies();

        List<Dependency> dependencies;
        if (!searchTransitive) {
            // the direct dependencies, the children of the root node
            dependencies = resolved.getRoot().getChildren().stream()
                    .map(Node::getDependency)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            dependencies = dependencyGraphToList(resolved.getRoot());
        }
        List<Dependency> foundExcludes = checkDependencies(filterDependencies(dependencies), files);
        // if any are found, fail the check but list all of them
        if (!foundExcludes.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            if (message != null) {
                buf.append(message).append("\n");
            }
            for (Dependency dependency : foundExcludes) {
                buf.append("Found Banned Dependency: ")
                        .append(ArtifactUtils.toId(dependency))
                        .append("\n");
            }
            message = buf + "Use 'mvn dependency:tree' to locate the source of the banned dependencies.";

            throw new EnforcerRuleException(message);
        }
    }

    private void validateAndPopulateParameters() throws EnforcerRuleException {
        if (maxJdkVersion != null && maxJavaMajorVersionNumber != -1) {
            throw new IllegalArgumentException("Only maxJdkVersion or maxJavaMajorVersionNumber "
                    + "configuration parameters should be set. Not both.");
        }
        if (maxJdkVersion == null && maxJavaMajorVersionNumber == -1) {
            throw new IllegalArgumentException(
                    "Exactly one of maxJdkVersion or maxJavaMajorVersionNumber options should be set.");
        }
        if (maxJdkVersion != null) {
            Integer needle = decodeMajorVersion(maxJdkVersion);
            if (needle == null) {
                throw new IllegalArgumentException(
                        "Unknown JDK version given. Should be something like \"8\", \"11\", \"17\", \"21\" ..");
            }
            maxJavaMajorVersionNumber = needle;
            if (!strict && needle < 53) {
                getLog().debug("Ignore: module-info (target < Java 8)");
                ignorableClassesPatterns.addAll(Arrays.asList(DEFAULT_CLASSES_IGNORE_BEFORE_JDK_9));
            }
        }
        if (maxJavaMajorVersionNumber == -1) {
            throw new EnforcerRuleException("maxJavaMajorVersionNumber must be set in the plugin configuration");
        }
        if (ignoreClasses != null) {
            ignorableClassesPatterns.addAll(ignoreClasses);
        }
    }

    private List<Dependency> checkDependencies(List<Dependency> dependencies, Map<Dependency, Path> files)
            throws EnforcerRuleException {
        long beforeCheck = System.currentTimeMillis();
        List<Dependency> problematic = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            getLog().debug("Analyzing artifact " + dependency);
            String problem = isBadDependency(dependency, files.get(dependency));
            if (problem != null) {
                getLog().info(problem);
                problematic.add(dependency);
            }
        }
        getLog().debug("Bytecode version analysis took " + (System.currentTimeMillis() - beforeCheck) + " ms");
        return problematic;
    }

    private String isBadDependency(Dependency d, Path path) throws EnforcerRuleException {
        getLog().debug("isBadDependency() a:" + d + " Artifact path:" + path);
        if (path == null) {
            // This happens if someone defines dependencies instead of dependencyManagement in a pom file
            // which packaging type is pom.
            return null;
        }
        File f = path.toFile();
        if (!f.getName().endsWith(".jar")) {
            return null;
        }
        // check did we do this already?
        ChecksOptions checksOptions = new ChecksOptions(
                ArtifactUtils.toId(d),
                f,
                ignorableClassesPatterns,
                maxJavaMajorVersionNumber,
                maxJavaMinorVersionNumber,
                strict);

        ConcurrentMap<ChecksOptions, Boolean> performedChecks =
                session.getData().computeIfAbsent(PERFORMED_CHECKS, ConcurrentHashMap::new);
        if (processOncePerSession && performedChecks.putIfAbsent(checksOptions, Boolean.TRUE) != null) {
            // we already performed checks on this file with these parameters
            return null;
        }

        return performCheck(getLog(), checksOptions);
    }

    private static class ChecksOptions {
        private final String id;
        private final File file;
        private final List<String> ignorableClasses;
        private final int maxJavaMajorVersionNumber;
        private final int maxJavaMinorVersionNumber;
        private final boolean strict;

        private ChecksOptions(
                String id,
                File file,
                List<String> ignorableClasses,
                int maxJavaMajorVersionNumber,
                int maxJavaMinorVersionNumber,
                boolean strict) {
            this.id = id;
            this.file = file;
            this.ignorableClasses = ignorableClasses;
            this.maxJavaMajorVersionNumber = maxJavaMajorVersionNumber;
            this.maxJavaMinorVersionNumber = maxJavaMinorVersionNumber;
            this.strict = strict;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ChecksOptions)) {
                return false;
            }
            ChecksOptions that = (ChecksOptions) o;
            return maxJavaMajorVersionNumber == that.maxJavaMajorVersionNumber
                    && maxJavaMinorVersionNumber == that.maxJavaMinorVersionNumber
                    && strict == that.strict
                    && Objects.equals(id, that.id)
                    && Objects.equals(file, that.file)
                    && Objects.equals(ignorableClasses, that.ignorableClasses);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    id, file, ignorableClasses, maxJavaMajorVersionNumber, maxJavaMinorVersionNumber, strict);
        }
    }

    /**
     * Convert a wildcard into a regex.
     *
     * @param wildcard the wildcard to convert.
     * @return the equivalent regex.
     */
    private static String asRegex(String wildcard) {
        StringBuilder result = new StringBuilder(wildcard.length());
        result.append('^');
        for (int index = 0; index < wildcard.length(); index++) {
            char character = wildcard.charAt(index);
            switch (character) {
                case '*':
                    result.append(".*");
                    break;
                case '?':
                    result.append(".");
                    break;
                case '$':
                case '(':
                case ')':
                case '.':
                case '[':
                case '\\':
                case ']':
                case '^':
                case '{':
                case '|':
                case '}':
                    result.append("\\");
                default:
                    result.append(character);
                    break;
            }
        }
        result.append("(\\.class)?");
        result.append('$');
        return result.toString();
    }

    private static String performCheck(EnforcerLogger log, ChecksOptions options) throws EnforcerRuleException {
        Predicate<String> ignorableClasses = null;
        for (String ignorableClass : options.ignorableClasses) {
            Pattern pattern = Pattern.compile(asRegex(ignorableClass.replace('.', '/')));
            Predicate<String> p = s -> pattern.matcher(s).matches();
            if (ignorableClasses == null) {
                ignorableClasses = p;
            } else {
                ignorableClasses = ignorableClasses.or(p);
            }
        }
        try (JarFile jarFile = new JarFile(options.file)) {
            log.debug(options.file.getName() + " => " + options.file.getPath());
            byte[] magicAndClassFileVersion = new byte[8];
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    if (ignorableClasses != null && ignorableClasses.test(entry.getName())) {
                        continue;
                    }

                    try (InputStream is = jarFile.getInputStream(entry)) {
                        int total = magicAndClassFileVersion.length;
                        while (total > 0) {
                            int read =
                                    is.read(magicAndClassFileVersion, magicAndClassFileVersion.length - total, total);
                            if (read == -1) {
                                throw new EOFException(options.file.toString());
                            }
                            total -= read;
                        }
                    }

                    int minor = (magicAndClassFileVersion[4] << 8) + magicAndClassFileVersion[5];
                    int major = (magicAndClassFileVersion[6] << 8) + magicAndClassFileVersion[7];

                    // Assuming regex match is more expensive, verify bytecode versions first

                    if ((major > options.maxJavaMajorVersionNumber)
                            || (major == options.maxJavaMajorVersionNumber
                                    && minor > options.maxJavaMinorVersionNumber)) {

                        Matcher matcher = MULTI_RELEASE.matcher(entry.getName());

                        if (!options.strict && matcher.matches()) {
                            Integer maxExpectedMajor = decodeMajorVersion(matcher.group(1));

                            if (maxExpectedMajor == null) {
                                log.warn("Unknown bytecodeVersion for " + options.id
                                        + " : " + entry.getName() + ": got " + maxExpectedMajor
                                        + " class-file-version");
                            } else if (major > maxExpectedMajor) {
                                log.warn("Invalid bytecodeVersion for " + options.id
                                        + " : " + entry.getName() + ": expected lower or equal to " + maxExpectedMajor
                                        + ", but was " + major);
                            }
                        } else {
                            return "Restricted to "
                                    + renderVersion(
                                            options.maxJavaMajorVersionNumber, options.maxJavaMinorVersionNumber)
                                    + " yet " + options.id + " contains " + entry.getName()
                                    + " targeted to "
                                    + renderVersion(major, minor);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new EnforcerRuleException("IOException while reading " + options.file, e);
        } catch (IllegalArgumentException e) {
            throw new EnforcerRuleException("Error while reading " + options.file, e);
        }
        return null;
    }

    private List<Dependency> dependencyGraphToList(Node root) {
        List<Dependency> result = new ArrayList<>();
        // the same node can be visited more than once, as in a tree it is the same dependency
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        root.accept(new NodeVisitor() {
            @Override
            public boolean enter(Node node) {
                if (!visited.add(node)) {
                    return false;
                }
                if (node.getDependency() != null) {
                    result.add(node.getDependency());
                }
                return true;
            }

            @Override
            public boolean leave(Node node) {
                return true;
            }
        });
        return result;
    }

    private List<Dependency> filterDependencies(List<Dependency> dependencies) {
        ArtifactMatcher excludedArtifactMatcher = null;
        if (includes != null || excludes != null) {
            // ArtifactMatcher returns true for artifacts selected by excludes unless an
            // include overrides that exclusion. Those artifacts must be skipped here.
            excludedArtifactMatcher = new ArtifactMatcher(versionParser, excludes, includes);
        }

        List<Dependency> filteredDependencies = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            if (excludedArtifactMatcher != null && excludedArtifactMatcher.match(dependency)) {
                getLog().debug("Skipping excluded dependency " + dependency);
                continue;
            }
            if (scopes != null && !scopes.contains(dependency.getScope().id())) {
                getLog().debug("Skipping " + dependency + " due to scope");
                continue;
            }
            filteredDependencies.add(dependency);
        }
        return filteredDependencies;
    }
}
