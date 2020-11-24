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

package com.gocypher.cybench.launcher.plugin;

import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.launcher.environment.model.HardwareProperties;
import com.gocypher.cybench.launcher.environment.model.JVMProperties;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.Constants;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.jcabi.manifests.Manifests;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.HotspotRuntimeProfiler;
import org.openjdk.jmh.profile.HotspotThreadProfiler;
import org.openjdk.jmh.profile.SafepointsProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.Utils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.util.*;

@Mojo( name = "cybench",requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class CyBenchLauncherMojo extends AbstractMojo {
    @Parameter(property = "cybench.forks", defaultValue = "1")
    private int forks = 1;

    @Parameter(property = "cybench.threads", defaultValue = "1")
    private int threads = 1;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "cybench.classpathScope", defaultValue = "runtime")
    protected String classpathScope;

    @Parameter(property = "cybench.measurementIterations", defaultValue = "1")
    private int measurementIterations = 1;

    /** The warm-up iteration count for all benchmarks*/
    @Parameter(property = "cybench.warmUpIterations", defaultValue = "1")
    private int warmUpIterations = 1;
    /** The warm-up time for all benchmarks*/
    @Parameter(property = "cybench.warmUpSeconds", defaultValue = "5")
    private int warmUpSeconds = 5;

    @Parameter(property = "cybench.expectedScore", defaultValue = "-1.0")
    private double expectedScore = -1.0d;

    @Parameter(property = "cybench.shouldSendReportToCyBench", defaultValue = "false")
    private boolean shouldSendReportToCyBench = false;

    @Parameter(property = "cybench.shouldStoreReportToFileSystem", defaultValue = "true")
    private boolean shouldStoreReportToFileSystem = true;

    @Parameter(property = "cybench.reportUploadStatus", defaultValue = "public")
    private String reportUploadStatus = "public";

    @Parameter(property = "cybench.reportsFolder", defaultValue = "")
    private String reportsFolder = "";

    @Parameter(property = "cybench.reportName", defaultValue = "CyBench Report")
    private String reportName = "CyBench Report";

    @Parameter(property = "cybench.customBenchmarkMetadata", defaultValue = "")
    private String userBenchmarkMetadata = "";

    @Parameter(property = "cybench.customProperties", defaultValue = "")
    private String userProperties = "";

    @Parameter(property = "cybench.customProperties", defaultValue = "false")
    private boolean skip;


    public void execute() throws MojoExecutionException {
//        getLog().info("_______________________ "+System.getProperty("skipCybench")+" __________________________");
        if(!skip && System.getProperty("skipCybench") == null ) {
            long start = System.currentTimeMillis();
            getLog().info("-----------------------------------------------------------------------------------------");
            getLog().info("                                 Starting CyBench benchmarks (Maven Plugin)                             ");
            getLog().info("-----------------------------------------------------------------------------------------");
            try {
                this.resolveAndUpdateClasspath(this.project, this.classpathScope);

                getLog().info("Collecting hardware, software information...");
                HardwareProperties hwProperties = CollectSystemInformation.getEnvironmentProperties();
                getLog().info("Collecting JVM properties...");
                JVMProperties jvmProperties = CollectSystemInformation.getJavaVirtualMachineProperties();

                //FIXME generate security hashes for report classes found on the classpath
                SecurityBuilder securityBuilder = new SecurityBuilder();

                Map<String, Object> benchmarkSettings = new HashMap<>();

                Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils.parseBenchmarkMetadata(userBenchmarkMetadata);

                this.checkAndConfigureCustomProperties(securityBuilder, benchmarkSettings, customBenchmarksMetadata);

                benchmarkSettings.put("benchThreadCount", threads);
                benchmarkSettings.put("benchReportName", reportName);

                getLog().info("Executing benchmarks...");

                OptionsBuilder optBuild = new OptionsBuilder();
                Options opt = optBuild.forks(forks)
                        .measurementIterations(measurementIterations)
                        .warmupIterations(warmUpIterations)
                        .warmupTime(TimeValue.seconds(warmUpIterations))
                        .threads(threads)
                        .shouldDoGC(true)
                        .addProfiler(GCProfiler.class)
                        .addProfiler(HotspotThreadProfiler.class)
                        .addProfiler(HotspotRuntimeProfiler.class)
                        .addProfiler(SafepointsProfiler.class)
                        .detectJvmArgs()
                        .build();

                Runner runner = new Runner(opt);

                Collection<RunResult> results = runner.run();

                BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results, customBenchmarksMetadata);
                report.updateUploadStatus(reportUploadStatus);

                report.getEnvironmentSettings().put("environment", hwProperties);
                report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
                report.getEnvironmentSettings().put("unclassifiedProperties", CollectSystemInformation.getUnclassifiedProperties());
                report.getEnvironmentSettings().put("userDefinedProperties", customUserDefinedProperties(userProperties));
                report.setBenchmarkSettings(benchmarkSettings);

                //FIXME add all missing custom properties including public/private flag

                getLog().info("-----------------------------------------------------------------------------------------");
                getLog().info("Report score - " + report.getTotalScore());
                getLog().info("-----------------------------------------------------------------------------------------");
                String reportJSON = JSONUtils.marshalToPrettyJson(report);
                getLog().info(reportJSON);
                if (expectedScore > 0) {
                    if (report.getTotalScore().doubleValue() < expectedScore) {
                        throw new MojoFailureException("CyBench score is less than expected:" + report.getTotalScore().doubleValue() + " < " + expectedScore);
                    }
                }

                String reportEncrypted = ReportingService.getInstance().prepareReportForDelivery(securityBuilder, report);

                String responseWithUrl = null;
                if (report.isEligibleForStoringExternally() && shouldSendReportToCyBench) {
                    responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted);
                    report.setReportURL(responseWithUrl);
                } else {
                    getLog().info("You may submit your report '" + IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE) + "' manually at " + Constants.CYB_UPLOAD_URL);
                }
                if (shouldStoreReportToFileSystem) {
                    getLog().info("Saving test results to '" + IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_JSON_FILE) + "'");
                    IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_JSON_FILE), reportJSON);
                    getLog().info("Saving encrypted test results to '" + IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE) + "'");
                    IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE), reportEncrypted);
                }
                getLog().info("Removing all temporary auto-generated files....");
                IOUtils.removeTestDataFiles();
                getLog().info("Removed all temporary auto-generated files!!!");


            } catch (Throwable t) {
                getLog().error(t);
                if (t.getMessage() != null && t.getMessage().contains("/META-INF/BenchmarkList")) {
                    getLog().info("-------------------No benchmark tests found-------------------");
                } else {
                    throw new MojoExecutionException("Error during benchmarks run", t);
                }
            }
            getLog().info("-----------------------------------------------------------------------------------------");
            getLog().info("         Finished CyBench benchmarking (" + ComputationUtils.formatInterval(System.currentTimeMillis() - start) + ")");
            getLog().info("-----------------------------------------------------------------------------------------");
        }else {
            getLog().info("Skipping CyBench execution");
        }
    }

    private void resolveAndUpdateClasspath (MavenProject project,String classpathScope) throws Exception{
        /*This part of code resolves project output directory and sets it to plugin class realm that it can find benchmark classes*/
        final File classes = new File(project.getBuild().getOutputDirectory());
        final PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");
        final ClassRealm classRealm = pluginDescriptor.getClassRealm();
        classRealm.addURL(classes.toURI().toURL());

        //getLog().info(project.getCompileClasspathElements().toString());
        //getLog().info(project.getRuntimeClasspathElements().toString());
        //getLog().info(project.getTestClasspathElements().toString());

        /*This part of code resolves libraries used in project and sets it to System classpath that JMH could use it.*/
        List<Artifact> artifacts = new ArrayList<Artifact>();
        List<File> theClasspathFiles = new ArrayList<File>();
        collectProjectArtifactsAndClasspath(artifacts, theClasspathFiles,classpathScope);
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
        String finalClassPath = System.getProperty("java.class.path")+tmpClasspath.toString() ;
        System.setProperty("java.class.path",finalClassPath);

        getLog().info("Benchmarks classpath:"+System.getProperty("java.class.path"));

    }
    private void collectProjectArtifactsAndClasspath(List<Artifact> artifacts, List<File> theClasspathFiles,String classpathScope) {

        if ("compile".equals(classpathScope)) {
            artifacts.addAll(project.getCompileArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if ("test".equals(classpathScope)) {
            artifacts.addAll(project.getTestArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getTestOutputDirectory()));
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if ("runtime".equals(classpathScope)) {
            artifacts.addAll(project.getRuntimeArtifacts());
            theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
        } else if ("system".equals(classpathScope)) {
            artifacts.addAll(project.getSystemArtifacts());
        } else {
            throw new IllegalStateException("Invalid classpath scope: " + classpathScope);
        }

        getLog().debug("Collected project artifacts " + artifacts);
        getLog().debug("Collected project classpath " + theClasspathFiles);
    }

    private void checkAndConfigureCustomProperties (SecurityBuilder securityBuilder
                                                    ,Map<String,Object>benchmarkSettings
                                                    ,Map<String,Map<String,String>>customBenchmarksMetadata){

        Reflections reflections = new Reflections("com.gocypher.cybench.", new SubTypesScanner(false));
        Set<Class<? extends Object>> allDefaultClasses = reflections.getSubTypesOf(Object.class);
        String tempBenchmark = null;
        for (Class<? extends Object> classObj : allDefaultClasses) {
            if (!classObj.getName().isEmpty() && classObj.getSimpleName().contains("Benchmarks")
                    && !classObj.getSimpleName().contains("_")) {
                // LOG.info("==>Default found:{}",classObj.getName());
                // We do not include any class, because then JMH will discover all benchmarks
                // automatically including custom ones.
                // optBuild.include(classObj.getName());
                tempBenchmark = classObj.getName();
                securityBuilder.generateSecurityHashForClasses(classObj);
            }
        }
        if (tempBenchmark != null) {
            String manifestData = null;
            if (Manifests.exists("customBenchmarkMetadata")) {
                manifestData = Manifests.read("customBenchmarkMetadata");
            }
            Map<String, Map<String, String>> benchmarksMetadata = ComputationUtils.parseBenchmarkMetadata(manifestData);
            Map<String, String> benchProps;
            if (manifestData != null) {
                benchProps = ReportingService.getInstance().prepareBenchmarkSettings(tempBenchmark, benchmarksMetadata);
            } else {
                benchProps = ReportingService.getInstance().prepareBenchmarkSettings(tempBenchmark, customBenchmarksMetadata);
            }
            benchmarkSettings.putAll(benchProps);
        }

    }
    private static Map<String, Object> customUserDefinedProperties(String customPropertiesStr) {
        Map<String, Object> customUserProperties = new HashMap<>();
        if (customPropertiesStr != null && !customPropertiesStr.isEmpty()){
            String [] pairs = customPropertiesStr.split(";") ;
            for (String pair:pairs){
                String [] kv = pair.split("=");
                if (kv.length == 2){
                    customUserProperties.put(kv[0],kv[1]) ;
                }
            }
        }


        return customUserProperties;
    }


}
