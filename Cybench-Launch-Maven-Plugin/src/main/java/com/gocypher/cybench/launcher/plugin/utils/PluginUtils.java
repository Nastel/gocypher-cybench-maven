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

import com.gocypher.cybench.core.annotation.BenchmarkMetaData;
import com.gocypher.cybench.core.annotation.CyBenchMetadataList;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.utils.Constants;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.openjdk.jmh.util.Utils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class PluginUtils {

    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_RUNTIME = "runtime";
    private static final String SCOPE_SYSTEM = "system";
    public static final String KEY_SKIP_CYBENCH = "skipCybench";
    public static final String KEY_SYSTEM_CLASSPATH = "java.class.path";
    static Properties cfg = new Properties();

    public static Map<String, Object> extractKeyValueProperties(String customPropertiesStr) {
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
        /*This part of code resolves project output directory and sets it Benchmarks classpath to plugin class realm that it can find benchmark classes*/
        final File classes = new File(project.getBuild().getOutputDirectory());
        final File classesTest = new File(project.getBuild().getTestOutputDirectory());
        final PluginDescriptor pluginDescriptor = (PluginDescriptor) pluginContext.get("pluginDescriptor");
        final ClassRealm classRealm = pluginDescriptor.getClassRealm();
        classRealm.addURL(classes.toURI().toURL());
        classRealm.addURL(classesTest.toURI().toURL());

        /*This part of code resolves libraries used in project and sets it to System classpath that JMH could use it.*/
        List<Artifact> artifacts = new ArrayList<Artifact>();
        List<File> theClasspathFiles = new ArrayList<File>();
        collectProjectArtifactsAndClasspathByScope(project, artifacts, theClasspathFiles, classpathScope);
        theClasspathFiles.add(new File(project.getBuild().getTestOutputDirectory()));
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
    public static String checkReportSaveLocation(String fileName){
        if(!fileName.endsWith("/")){
            fileName = fileName +"/";
        }
        return fileName;
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

    /**
     *  Resolve and add class annotation to report
     * @param aClass
     * @param benchmarkReport
     */
    public static void appendMetadataFromClass(Class<?> aClass, BenchmarkReport benchmarkReport) {
        CyBenchMetadataList annotation = aClass.getDeclaredAnnotation(CyBenchMetadataList.class);
        if (annotation != null) {
            Arrays.stream(annotation.value()).forEach(annot -> {
                checkSetOldMetadataProps(annot.key(), annot.value(), benchmarkReport);
                benchmarkReport.addMetadata(annot.key(), annot.value());
            });
        }
        BenchmarkMetaData singleAnnotation = aClass.getDeclaredAnnotation(BenchmarkMetaData.class);
        if (singleAnnotation != null) {
            checkSetOldMetadataProps(singleAnnotation.key(), singleAnnotation.value(), benchmarkReport);
            benchmarkReport.addMetadata(singleAnnotation.key(), singleAnnotation.value());
        }
    }

    /**
     *  Resolve and add method annotation to report
     * @param benchmarkMethod
     * @param benchmarkReport
     */
    public static void appendMetadataFromMethod(Optional<Method> benchmarkMethod, BenchmarkReport benchmarkReport) {
        CyBenchMetadataList annotation = benchmarkMethod.get().getDeclaredAnnotation(CyBenchMetadataList.class);
        if (annotation != null) {
            Arrays.stream(annotation.value()).forEach(annot -> {
                checkSetOldMetadataProps(annot.key(), annot.value(), benchmarkReport);
                benchmarkReport.addMetadata(annot.key(), annot.value());
            });
        }
        BenchmarkMetaData singleAnnotation = benchmarkMethod.get().getDeclaredAnnotation(BenchmarkMetaData.class);
        if (singleAnnotation != null) {
            checkSetOldMetadataProps(singleAnnotation.key(), singleAnnotation.value(), benchmarkReport);
            benchmarkReport.addMetadata(singleAnnotation.key(), singleAnnotation.value());

        }
    }

    /**
     *  A method needed in order to support the previous data model. Setting the needed values from annotation to a
     *  previously defined data model value
     * @param key
     * @param value
     * @param benchmarkReport
     */
    private static void checkSetOldMetadataProps(String key,String value, BenchmarkReport benchmarkReport){
        if(key.equals("api")){
            benchmarkReport.setCategory(value);
        }
        if(key.equals("context")){
            benchmarkReport.setContext(value);
        }
        if(key.equals("version")){
            benchmarkReport.setVersion(value);
        }
    }

    public static void getReportUploadStatus(BenchmarkOverviewReport report) {
        String reportUploadStatus = getProperty(Constants.REPORT_UPLOAD_STATUS);
        if (Constants.REPORT_PUBLIC.equals(reportUploadStatus)) {
            report.setUploadStatus(reportUploadStatus);
        } else if (Constants.REPORT_PRIVATE.equals(reportUploadStatus)) {
            report.setUploadStatus(reportUploadStatus);
        } else {
            report.setUploadStatus(Constants.REPORT_PUBLIC);
        }
    }

    public static String getProperty(String key) {
        return System.getProperty(key, cfg.getProperty(key));
    }

}

