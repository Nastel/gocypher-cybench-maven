package com.gocypher.cybench.launcher.plugin.utils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.openjdk.jmh.util.Utils;

import java.io.File;
import java.util.*;

public class PluginUtils {

    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_RUNTIME = "runtime";
    private static final String SCOPE_SYSTEM = "system";
    public static final String KEY_SKIP_CYBENCH = "skipCybench";
    public static final String KEY_SYSTEM_CLASSPATH = "java.class.path";


    public static Map<String, Object> extractCustomProperties(String customPropertiesStr) {
        Map<String, Object> customProperties = new HashMap<>();
        if (customPropertiesStr != null && !customPropertiesStr.isEmpty()) {
            String[] pairs = customPropertiesStr.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    customProperties.put(kv[0], kv[1]);
                }
            }
        }
        return customProperties;
    }


    public static void resolveAndUpdateClasspath(Log log, MavenProject project, Map pluginContext, String classpathScope) throws Exception {
        /*This part of code resolves project output directory and sets it to plugin class realm that it can find benchmark classes*/
        final File classes = new File(project.getBuild().getOutputDirectory());
        final PluginDescriptor pluginDescriptor = (PluginDescriptor) pluginContext.get("pluginDescriptor");
        final ClassRealm classRealm = pluginDescriptor.getClassRealm();
        classRealm.addURL(classes.toURI().toURL());

        //getLog().info(project.getCompileClasspathElements().toString());
        //getLog().info(project.getRuntimeClasspathElements().toString());
        //getLog().info(project.getTestClasspathElements().toString());

        /*This part of code resolves libraries used in project and sets it to System classpath that JMH could use it.*/
        List<Artifact> artifacts = new ArrayList<Artifact>();
        List<File> theClasspathFiles = new ArrayList<File>();

        collectProjectArtifactsAndClasspathByScope(project, artifacts, theClasspathFiles, classpathScope);

        Set<String> classPaths = new HashSet<String>();

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

        /* This update of the classpath is required in order to successfully launch JMH forked JVM's correctly and avoid failures because of missing classpath libraries. JMH forked JVM's inherits System classpath.*/
        String finalClassPath = System.getProperty(KEY_SYSTEM_CLASSPATH) + tmpClasspath.toString();
        System.setProperty(KEY_SYSTEM_CLASSPATH, finalClassPath);

        log.info("Benchmarks classpath:" + System.getProperty(KEY_SYSTEM_CLASSPATH));

    }

    private static void collectProjectArtifactsAndClasspathByScope(MavenProject project,
                                                                   List<Artifact> artifacts,
                                                                   List<File> theClasspathFiles,
                                                                   String classpathScope) {

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

