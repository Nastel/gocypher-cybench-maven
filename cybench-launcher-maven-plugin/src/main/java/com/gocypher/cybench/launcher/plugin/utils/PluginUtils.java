/*
 * Copyright (C) 2020, K2N.IO.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package com.gocypher.cybench.launcher.plugin.utils;

import java.io.File;
import java.util.*;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.openjdk.jmh.util.Utils;

public final class PluginUtils {

    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_RUNTIME = "runtime";
    private static final String SCOPE_SYSTEM = "system";
    public static final String KEY_SKIP_CYBENCH = "skipCybench";
    public static final String KEY_SYSTEM_CLASSPATH = "java.class.path";

    private PluginUtils() {
    }

    public static void resolveAndUpdateClasspath(Log log, MavenProject project, Map<String, ?> pluginContext,
            String classpathScope) throws Exception {
        /*
         * This part of code resolves project output directory and sets it Benchmarks classpath to plugin class realm
         * that it can find benchmark classes
         */
        File classes = new File(project.getBuild().getOutputDirectory());
        File classesTest = new File(project.getBuild().getTestOutputDirectory());
        PluginDescriptor pluginDescriptor = (PluginDescriptor) pluginContext.get("pluginDescriptor");
        ClassRealm classRealm = pluginDescriptor.getClassRealm();
        classRealm.addURL(classes.toURI().toURL());
        classRealm.addURL(classesTest.toURI().toURL());

        /*
         * This part of code resolves libraries used in project and sets it to System classpath that JMH could use it.
         */
        List<Artifact> artifacts = new ArrayList<>();
        List<File> theClasspathFiles = new ArrayList<>();
        collectProjectArtifactsAndClasspathByScope(project, artifacts, theClasspathFiles, classpathScope);
        theClasspathFiles.add(new File(project.getBuild().getTestOutputDirectory()));
        Set<String> classPaths = new HashSet<>();
        for (File f : theClasspathFiles) {
            classPaths.add(f.getAbsolutePath());
        }

        for (Artifact artifact : artifacts) {
            classPaths.add(artifact.getFile().getAbsolutePath());
        }
        StringBuilder tmpClasspath = new StringBuilder();

        if (classPaths != null) {
            for (String classPath : classPaths) {
                if (Utils.isWindows()) {
                    tmpClasspath.append(";").append(classPath);
                } else {
                    tmpClasspath.append(":").append(classPath);
                }
            }
        }
        /*
         * This update of the classpath is required in order to successfully launch JMH forked JVM's correctly and avoid
         * failures because of missing classpath libraries. JMH forked JVM's inherits System classpath.
         */
        String finalClassPath = System.getProperty(KEY_SYSTEM_CLASSPATH) + tmpClasspath;
        System.setProperty(KEY_SYSTEM_CLASSPATH, finalClassPath);

        log.info("Benchmarks classpath:" + System.getProperty(KEY_SYSTEM_CLASSPATH));

    }

    public static String checkReportSaveLocation(String fileName) {
        if (!fileName.endsWith("/")) {
            fileName = fileName + "/";
        }
        return fileName;
    }

    private static void collectProjectArtifactsAndClasspathByScope(MavenProject project, List<Artifact> artifacts,
            List<File> theClasspathFiles, String classpathScope) {
        if (SCOPE_COMPILE.equals(classpathScope)) {
            artifacts.addAll(project.getCompileArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if (SCOPE_TEST.equals(classpathScope)) {
            artifacts.addAll(project.getTestArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getTestOutputDirectory()));
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if (SCOPE_RUNTIME.equals(classpathScope)) {
            artifacts.addAll(project.getRuntimeArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if (SCOPE_SYSTEM.equals(classpathScope)) {
            artifacts.addAll(project.getSystemArtifacts());
        } else {
            throw new IllegalStateException("Invalid classpath scope: " + classpathScope);
        }
    }
}
