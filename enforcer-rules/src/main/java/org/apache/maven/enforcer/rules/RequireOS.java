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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.model.Activation;
import org.apache.maven.api.model.ActivationOS;
import org.apache.maven.api.model.InputLocation;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.services.BuilderProblem;
import org.apache.maven.api.services.ModelBuilderException;
import org.apache.maven.api.services.ModelProblem;
import org.apache.maven.api.services.ModelProblemCollector;
import org.apache.maven.api.services.OsService;
import org.apache.maven.api.services.ProblemCollector;
import org.apache.maven.api.services.model.ProfileActivationContext;
import org.apache.maven.api.services.model.ProfileActivator;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.OSUtil;

/**
 * This rule checks that the OS is allowed by combinations of family, name, version and cpu architecture. The behavior
 * is exactly the same as the Maven Os profile activation so the same values are allowed here.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
@Named("requireOS")
public final class RequireOS extends AbstractStandardEnforcerRule {
    /**
     * The valid OS families, the same as in Maven OS profile activation.
     */
    private static final Set<String> VALID_FAMILIES = new TreeSet<>(Arrays.asList(
            "dos", "mac", "netware", "os/2", "tandem", "unix", "windows", "win9x", "z/os", "os/400", "openvms"));

    private final ProfileActivator activator;

    private final ProfileActivationContext profileActivationContext;

    private final OsService osService;

    /**
     * The OS family type desired<br />
     * Possible values:
     * <ul>
     * <li>dos</li>
     * <li>mac</li>
     * <li>netware</li>
     * <li>os/2</li>
     * <li>tandem</li>
     * <li>unix</li>
     * <li>windows</li>
     * <li>win9x</li>
     * <li>z/os</li>
     * <li>os/400</li>
     * </ul>
     */
    private String family = null;

    /**
     * The OS name desired.
     */
    private String name = null;

    /**
     * The OS version desired.
     */
    private String version = null;

    /**
     * The OS architecture desired.
     */
    private String arch = null;

    /**
     * Display detected OS information.
     */
    private boolean display = false;

    /**
     * Instantiates a new RequireOS.
     */
    @Inject
    RequireOS(@Named("os") ProfileActivator activator, Session session, OsService osService) {
        this.activator = Objects.requireNonNull(activator);
        this.osService = Objects.requireNonNull(osService);
        this.profileActivationContext = createProfileActivationContext(Objects.requireNonNull(session));
    }

    private static ProfileActivationContext createProfileActivationContext(Session session) {
        return new SessionProfileActivationContext(session);
    }

    @Override
    public void execute() throws EnforcerRuleException {

        displayOSInfo();

        if (allParamsEmpty()) {
            throw new EnforcerRuleError("All parameters can not be empty. "
                    + "You must pick at least one of (family, name, version, arch), "
                    + "you can use mvn --version to see the current OS information.");
        }

        if (isValidFamily(this.family)) {
            if (!isAllowed()) {
                String message = getMessage();
                if (message == null || message.isEmpty()) {
                    // @formatter:off
                    message = "OS Arch: "
                            + osService.arch() + " Family: "
                            + osService.family() + " Name: "
                            + osService.name() + " Version: "
                            + osService.version() + " is not allowed by" + (arch != null ? " Arch=" + arch : "")
                            + (family != null ? " Family=" + family : "")
                            + (name != null ? " Name=" + name : "")
                            + (version != null ? " Version=" + version : "");
                    // @formatter:on
                }
                throw new EnforcerRuleException(message);
            }
        } else {
            String validFamilies = String.join(",", VALID_FAMILIES);
            throw new EnforcerRuleError("Invalid Family type used. Valid family types are: " + validFamilies);
        }
    }

    /**
     * Log the current OS information.
     */
    private void displayOSInfo() {
        String string = OSUtil.getOSInfo(osService);

        if (!display) {
            getLog().debug(string);
        } else {
            getLog().info(string);
        }
    }

    /**
     * Helper method to determine if the current OS is allowed based on the injected values for family, name, version
     * and arch.
     *
     * @return true if the version is allowed.
     */
    public boolean isAllowed() {
        // empty lambda as problems collector
        return activator.isActive(createProfile(), profileActivationContext, new NoOpProblemCollector());
    }

    /**
     * Helper method to check that at least one of family, name, version or arch is set.
     *
     * @return true if all parameters are empty.
     */
    public boolean allParamsEmpty() {
        return (family == null || family.isEmpty())
                && (arch == null || arch.isEmpty())
                && (name == null || name.isEmpty())
                && (version == null || version.isEmpty());
    }

    /**
     * Creates a Profile object that contains the activation information.
     *
     * @return a properly populated profile to be used for OS validation.
     */
    private Profile createProfile() {
        return Profile.newBuilder().activation(createActivation()).build();
    }

    private Activation createActivation() {
        return Activation.newBuilder().activeByDefault(false).os(createOsBean()).build();
    }

    private ActivationOS createOsBean() {
        return ActivationOS.newBuilder()
                .arch(arch)
                .family(family)
                .name(name)
                .version(version)
                .build();
    }

    public boolean isValidFamily(String family) {

        // in case they are checking !family
        family = StringUtils.stripStart(family, "!");

        return (family == null || family.isEmpty()) || VALID_FAMILIES.contains(family);
    }

    /**
     * Sets the architecture.
     *
     * @param architecture the architecture to set
     */
    public void setArch(String architecture) {
        this.arch = architecture;
    }

    /**
     * Sets the family.
     *
     * @param family the family to set
     */
    public void setFamily(String family) {
        this.family = family;
    }

    /**
     * Sets the name.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the version.
     *
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @param display The value for the display.
     */
    public void setDisplay(boolean display) {
        this.display = display;
    }

    @Override
    public String getCacheId() {
        // return the hash codes of all the parameters
        StringBuilder b = new StringBuilder();
        if (version != null && !version.isEmpty()) {
            b.append(version.hashCode());
        }
        if (name != null && !name.isEmpty()) {
            b.append(name.hashCode());
        }
        if (arch != null && !arch.isEmpty()) {
            b.append(arch.hashCode());
        }
        if (family != null && !family.isEmpty()) {
            b.append(family.hashCode());
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "RequireOS[message=%s, arch=%s, family=%s, name=%s, version=%s, display=%b]",
                getMessage(), arch, family, name, version, display);
    }

    /**
     * The OS activator reads only system properties, the rest of the context is not available in the API.
     */
    private static final class SessionProfileActivationContext implements ProfileActivationContext {

        private final Session session;

        SessionProfileActivationContext(Session session) {
            this.session = session;
        }

        @Override
        public boolean isProfileActive(String profileId) {
            return false;
        }

        @Override
        public boolean isProfileInactive(String profileId) {
            return false;
        }

        @Override
        public String getSystemProperty(String key) {
            return System.getProperty(key);
        }

        @Override
        public String getUserProperty(String key) {
            return session.getUserProperties().get(key);
        }

        @Override
        public String getModelProperty(String key) {
            return null;
        }

        @Override
        public String getModelArtifactId() {
            return null;
        }

        @Override
        public String getModelPackaging() {
            return null;
        }

        @Override
        public String getModelRootDirectory() {
            return null;
        }

        @Override
        public String getModelBaseDirectory() {
            return null;
        }

        @Override
        public String interpolatePath(String path) {
            return path;
        }

        @Override
        public boolean exists(String path, boolean enableRegex) {
            return false;
        }
    }

    private static final class NoOpProblemCollector implements ModelProblemCollector {

        @Override
        public ProblemCollector<ModelProblem> getProblemCollector() {
            return ProblemCollector.empty();
        }

        @Override
        public void add(
                BuilderProblem.Severity severity,
                ModelProblem.Version version,
                String message,
                InputLocation location,
                Exception exception) {}

        @Override
        public ModelBuilderException newModelBuilderException() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSource(String source) {}

        @Override
        public void setSource(Model source) {}

        @Override
        public String getSource() {
            return null;
        }

        @Override
        public void setRootModel(Model rootModel) {}

        @Override
        public Model getRootModel() {
            return null;
        }
    }
}
