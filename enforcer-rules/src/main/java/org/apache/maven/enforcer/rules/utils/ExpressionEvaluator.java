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

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;

/**
 * Evaluates {@code ${...}} expressions used by rules.
 * <p>
 * The Maven 4 API has no equivalent of {@code PluginParameterExpressionEvaluator}, so this class supports only the
 * expression roots that can be reached from the API: {@code project}/{@code pom}, {@code session}, {@code settings},
 * {@code basedir}, {@code env.*} and properties (session and project properties). {@code mojo}, {@code plugin} and
 * {@code localRepository} roots are not supported.
 *
 * @author Slawomir Jaranowski
 * @since 3.2.0
 */
@Named
public class ExpressionEvaluator {

    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");

    private final Session session;

    private final Project project;

    @Inject
    public ExpressionEvaluator(Session session, Project project) {
        this.session = Objects.requireNonNull(session);
        this.project = Objects.requireNonNull(project);
    }

    /**
     * Evaluates an expression.
     *
     * @param expression the expression, can contain {@code ${...}} references
     * @return the value of expression, {@code null} if the whole expression is a single reference which cannot be
     *         resolved; references inside a longer text which cannot be resolved are left untouched
     */
    public Object evaluate(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher matcher = EXPRESSION.matcher(expression);
        if (matcher.matches()) {
            return resolve(matcher.group(1));
        }
        matcher.reset();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = resolve(matcher.group(1));
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(value != null ? value.toString() : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Makes a relative file absolute against the base directory of the project.
     *
     * @param file the file to align
     * @return the aligned file
     */
    public File alignToBaseDirectory(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(project.getBasedir().toFile(), file.getPath());
    }

    private Object resolve(String name) {
        if ("basedir".equals(name)) {
            return project.getBasedir().toAbsolutePath().toString();
        }
        if (name.startsWith("env.")) {
            return System.getenv(name.substring("env.".length()));
        }
        String root = firstSegment(name);
        Object rootObject = null;
        switch (root) {
            case "project":
            case "pom":
                rootObject = project;
                break;
            case "session":
                rootObject = session;
                break;
            case "settings":
                rootObject = session.getSettings();
                break;
            default:
                break;
        }
        if (rootObject != null) {
            if (name.equals(root)) {
                return rootObject;
            }
            Object value = navigate(rootObject, name.substring(root.length() + 1));
            if (value != null || !"project".equals(root)) {
                return value;
            }
        }
        return session.getEffectiveProperties(project).get(name);
    }

    private static String firstSegment(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Object navigate(Object object, String path) {
        Object current = object;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = property(current, segment);
        }
        return current;
    }

    private static Object property(Object object, String name) {
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(name);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method method = findMethod(object.getClass(), prefix + suffix);
                if (method != null) {
                    Object value = method.invoke(object);
                    return value instanceof java.util.Optional ? ((java.util.Optional<?>) value).orElse(null) : value;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Cannot evaluate '" + name + "' on " + object.getClass(), e);
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                Method m = findMethod(i, methodName);
                if (m != null) {
                    return m;
                }
            }
            try {
                Method m = c.getMethod(methodName);
                if (m.getParameterCount() == 0) {
                    return m;
                }
            } catch (NoSuchMethodException e) {
                // try next
            }
        }
        return null;
    }
}
