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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.ReportPlugin;

/**
 * Resolves expressions in the coordinates of plugins.
 *
 * @author Slawomir Jaranowski
 * @since 3.2.0
 */
@Named
public class EnforcerRuleUtils {

    private final ExpressionEvaluator evaluator;

    @Inject
    public EnforcerRuleUtils(ExpressionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    private Plugin resolve(Plugin plugin) {
        return plugin.withGroupId((String) evaluator.evaluate(plugin.getGroupId()))
                .withArtifactId((String) evaluator.evaluate(plugin.getArtifactId()))
                .withVersion((String) evaluator.evaluate(plugin.getVersion()));
    }

    private ReportPlugin resolve(ReportPlugin plugin) {
        return plugin.withGroupId((String) evaluator.evaluate(plugin.getGroupId()))
                .withArtifactId((String) evaluator.evaluate(plugin.getArtifactId()))
                .withVersion((String) evaluator.evaluate(plugin.getVersion()));
    }

    /**
     * Resolves expressions in the coordinates of plugins.
     *
     * @param plugins the plugins
     * @return new plugins, the given ones are immutable
     */
    public List<Plugin> resolvePlugins(List<Plugin> plugins) {
        List<Plugin> result = new ArrayList<>(plugins.size());
        for (Plugin plugin : plugins) {
            result.add(resolve(plugin));
        }
        return result;
    }

    /**
     * Resolves expressions in the coordinates of report plugins.
     *
     * @param reportPlugins the report plugins
     * @return new report plugins, the given ones are immutable
     */
    public List<ReportPlugin> resolveReportPlugins(List<ReportPlugin> reportPlugins) {
        List<ReportPlugin> result = new ArrayList<>(reportPlugins.size());
        for (ReportPlugin plugin : reportPlugins) {
            result.add(resolve(plugin));
        }
        return result;
    }
}
