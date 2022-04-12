/*
 * Copyright (C) 2020-2022, K2N.IO.
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

package com.gocypher.cybench.launcher.plugin;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.SafepointsProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JMHUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.core.utils.SecurityUtils;
import com.gocypher.cybench.launcher.BenchmarkRunner;
import com.gocypher.cybench.launcher.environment.model.HardwareProperties;
import com.gocypher.cybench.launcher.environment.model.JVMProperties;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.plugin.utils.PluginUtils;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.Constants;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.gocypher.cybench.model.ComparisonConfig;

@Mojo(name = "cybench", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class CyBenchLauncherMojo extends AbstractMojo {
    private static final String benchSource = "Maven plugin";
    @Parameter(property = "cybench.classpathScope", defaultValue = "runtime")
    protected String classpathScope;
    @Parameter(property = "cybench.forks", defaultValue = "1")
    private int forks = 1;
    @Parameter(property = "cybench.threads", defaultValue = "1")
    private int threads = 1;
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    @Parameter(property = "cybench.measurementIterations", defaultValue = "5")
    private int measurementIterations = 5;
    @Parameter(property = "cybench.measurementTime", defaultValue = "10")
    private int measurementTime = 10;
    /**
     * The warm-up iteration count for all benchmarks
     */
    @Parameter(property = "cybench.warmUpIterations", defaultValue = "3")
    private int warmUpIterations = 3;
    /**
     * The warm-up time for all benchmarks
     */
    @Parameter(property = "cybench.warmUpTime", defaultValue = "5")
    private int warmUpTime = 5;
    @Parameter(property = "cybench.expectedScore", defaultValue = "-1.0")
    private double expectedScore = -1.0d;
    @Parameter(property = "cybench.shouldSendReportToCyBench", defaultValue = "false")
    private boolean shouldSendReportToCyBench = false;
    @Parameter(property = "cybench.shouldStoreReportToFileSystem", defaultValue = "true")
    private boolean shouldStoreReportToFileSystem = true;
    @Parameter(property = "cybench.reportUploadStatus", defaultValue = "public")
    private String reportUploadStatus = "public";
    @Parameter(property = "cybench.reportsFolder", defaultValue = "./reports")
    private String reportsFolder = "./reports";
    @Parameter(property = "cybench.reportName", defaultValue = "CyBench Report")
    private String reportName = "CyBench Report";
    @Parameter(property = "cybench.customBenchmarkMetadata", defaultValue = "")
    private String customBenchmarkMetadata = "";
    @Parameter(property = "cybench.userProperties", defaultValue = "")
    private String userProperties = "";
    @Parameter(property = "cybench.skip", defaultValue = "false")
    private boolean skip = false;
    @Parameter(property = "cybench.shouldFailBuildOnReportDeliveryFailure", defaultValue = "false")
    private boolean shouldFailBuildOnReportDeliveryFailure = false;
    @Parameter(property = "cybench.useCyBenchBenchmarkSettings", defaultValue = "true")
    private boolean useCyBenchBenchmarkSettings = true;
    @Parameter(property = "cybench.jvmArgs", defaultValue = "")
    private String jmvArgs = "";
    @Parameter(property = "cybench.benchAccessToken", defaultValue = "")
    private String benchAccessToken = "";
    @Parameter(property = "cybench.benchQueryToken", defaultValue = "")
    private String benchQueryToken = "";
    @Parameter(property = "cybench.email", defaultValue = "")
    private String email = "";

    @Parameter(property = "cybench.automationScope", defaultValue = "")
    private String automationScope;
    @Parameter(property = "cybench.automationCompareVersion", defaultValue = "")
    private String automationCompareVersion;
    @Parameter(property = "cybench.automationNumLatestReports", defaultValue = "")
    private int automationNumLatestReports;
    @Parameter(property = "cybench.automationAnomaliesAllowed", defaultValue = "")
    private int automationAnomaliesAllowed;
    @Parameter(property = "cybench.automationMethod", defaultValue = "")
    private String automationMethod;
    @Parameter(property = "cybench.automationThreshold", defaultValue = "")
    private String automationThreshold;
    @Parameter(property = "cybench.automationPercentChangeAllowed", defaultValue = "")
    private double automationPercentChangeAllowed;
    @Parameter(property = "cybench.automationDeviationsAllowed", defaultValue = "")
    private double automationDeviationsAllowed;

    @Override
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException {
        if (!skip && System.getProperty(PluginUtils.KEY_SKIP_CYBENCH) == null) {
            System.setProperty("collectHw", "true");
            boolean isReportSentSuccessFully = false;
            long start = System.currentTimeMillis();
            getLog().info("-----------------------------------------------------------------------------------------");
            getLog().info(
                    "                                 Starting CyBench benchmarks (Maven Plugin)                             ");
            getLog().info("-----------------------------------------------------------------------------------------");

            ComparisonConfig automatedComparisonCfg;
            try {
                automatedComparisonCfg = checkConfigValidity();
                getLog().info("** Configuration loaded: automated comparison configuration");
            } catch (Exception e) {
                automatedComparisonCfg = null;
                getLog().error("Failed to parse automated comparison configuration", e);
            }

            try {
                PluginUtils.resolveAndUpdateClasspath(getLog(), project, getPluginContext(), classpathScope);

                getLog().info("Collecting hardware, software information...");
                HardwareProperties hwProperties = CollectSystemInformation.getEnvironmentProperties();
                getLog().info("Collecting JVM properties...");
                JVMProperties jvmProperties = CollectSystemInformation.getJavaVirtualMachineProperties();

                SecurityBuilder securityBuilder = new SecurityBuilder();
                Map<String, Object> benchmarkSettings = new HashMap<>();
                Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils
                        .parseBenchmarkMetadata(customBenchmarkMetadata);

                benchmarkSettings.put("benchSource", benchSource);
                benchmarkSettings.put("benchWarmUpIteration", warmUpIterations);
                benchmarkSettings.put("benchWarmUpSeconds", warmUpTime);
                benchmarkSettings.put("benchMeasurementIteration", measurementIterations);
                benchmarkSettings.put("benchMeasurementSeconds", measurementTime);
                benchmarkSettings.put("benchForkCount", forks);
                benchmarkSettings.put("benchThreadCount", threads);

                if (StringUtils.isEmpty(reportName)) {
                    reportName = MessageFormat.format("Benchmark for {0}:{1}:{2}", project.getGroupId(),
                            project.getArtifactId(), project.getVersion());
                }
                benchmarkSettings.put("benchReportName", reportName);

                Map<String, String> PROJECT_METADATA_MAP = new HashMap<>();
                PROJECT_METADATA_MAP.put(Constants.PROJECT_NAME, project.getArtifactId());
                PROJECT_METADATA_MAP.put(Constants.PROJECT_VERSION, project.getVersion());

                getLog().info("Executing benchmarks...");

                OptionsBuilder optBuild = new OptionsBuilder();
                Options opt;
                if (useCyBenchBenchmarkSettings) {
                    ChainedOptionsBuilder chainedOptionsBuilder = optBuild.forks(forks)
                            .measurementTime(TimeValue.seconds(measurementTime))
                            .measurementIterations(measurementIterations).warmupIterations(warmUpIterations)
                            .warmupTime(TimeValue.seconds(warmUpTime)).threads(threads).shouldDoGC(true)
                            .addProfiler(GCProfiler.class)
                            // .addProfiler(HotspotThreadProfiler.class) //obsolete
                            // .addProfiler(HotspotRuntimeProfiler.class) //obsolete
                            .addProfiler(SafepointsProfiler.class);
                    if (jmvArgs.length() > 0) {
                        chainedOptionsBuilder.jvmArgs(jmvArgs);
                    } else {
                        chainedOptionsBuilder.detectJvmArgs();
                    }
                    opt = chainedOptionsBuilder.build();
                } else {
                    opt = optBuild.shouldDoGC(true).addProfiler(GCProfiler.class)
                            // .addProfiler(HotspotThreadProfiler.class) //obsolete
                            // .addProfiler(HotspotRuntimeProfiler.class) //obsolete
                            .addProfiler(SafepointsProfiler.class).detectJvmArgs().build();
                }

                Runner runner = new Runner(opt);
                Map<String, String> generatedFingerprints = new HashMap<>();
                Map<String, String> manualFingerprints = new HashMap<>();
                Map<String, String> classFingerprints = new HashMap<>();

                List<String> benchmarkNames = JMHUtils.getAllBenchmarkClasses();
                for (String benchmarkClass : benchmarkNames) {
                    try {
                        Class<?> classObj = Class.forName(benchmarkClass);
                        SecurityUtils.generateMethodFingerprints(classObj, manualFingerprints, classFingerprints);
                        SecurityUtils.computeClassHashForMethods(classObj, generatedFingerprints);
                    } catch (ClassNotFoundException exc) {
                        getLog().error("Class not found in the classpath for execution", exc);
                    }

                }

                Collection<RunResult> results = runner.run();

                BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results,
                        customBenchmarksMetadata);
                report.updateUploadStatus(reportUploadStatus);

                report.getEnvironmentSettings().put("environment", hwProperties);
                report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
                report.getEnvironmentSettings().put("unclassifiedProperties",
                        CollectSystemInformation.getUnclassifiedProperties());
                report.getEnvironmentSettings().put("userDefinedProperties",
                        ComputationUtils.customUserDefinedProperties(userProperties));
                report.setBenchmarkSettings(benchmarkSettings);

                if (automatedComparisonCfg != null) {
                    if (automatedComparisonCfg.getScope().equals(ComparisonConfig.Scope.WITHIN)) {
                        automatedComparisonCfg.setCompareVersion(PROJECT_METADATA_MAP.get(Constants.PROJECT_VERSION));
                    }
                    automatedComparisonCfg.setRange(String.valueOf(automatedComparisonCfg.getCompareLatestReports()));
                    automatedComparisonCfg.setProjectName(PROJECT_METADATA_MAP.get(Constants.PROJECT_NAME));
                    automatedComparisonCfg.setProjectVersion(PROJECT_METADATA_MAP.get(Constants.PROJECT_VERSION));
                    report.setAutomatedComparisonConfig(automatedComparisonCfg);
                }

                for (String s : report.getBenchmarks().keySet()) {
                    List<BenchmarkReport> custom = new ArrayList<>(report.getBenchmarks().get(s));
                    custom.forEach(benchmarkReport -> {
                        String name = benchmarkReport.getName();
                        benchmarkReport.setClassFingerprint(classFingerprints.get(name));
                        benchmarkReport.setGeneratedFingerprint(generatedFingerprints.get(name));
                        benchmarkReport.setManualFingerprint(manualFingerprints.get(name));
                        try {
                            JMHUtils.ClassAndMethod classAndMethod = new JMHUtils.ClassAndMethod(name).invoke();
                            String clazz = classAndMethod.getClazz();
                            String method = classAndMethod.getMethod();
                            getLog().info("Adding metadata for benchmark: " + clazz + " test: " + method);
                            Class<?> aClass = Class.forName(clazz);
                            Optional<Method> benchmarkMethod = JMHUtils.getBenchmarkMethod(method, aClass);
                            BenchmarkRunner.appendMetadataFromAnnotated(benchmarkMethod, benchmarkReport);
                            BenchmarkRunner.appendMetadataFromAnnotated(Optional.of(aClass), benchmarkReport);
                            BenchmarkRunner.syncReportsMetadata(report, benchmarkReport);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    });
                }
                List<BenchmarkReport> customBenchmarksCategoryCheck = report.getBenchmarks().get("CUSTOM");
                report.getBenchmarks().remove("CUSTOM");
                for (BenchmarkReport benchReport : customBenchmarksCategoryCheck) {
                    report.addToBenchmarks(benchReport);
                }
                report.computeScores();
                // BenchmarkRunner.getReportUploadStatus(report);
                getLog().info(
                        "-----------------------------------------------------------------------------------------");
                getLog().info("Report score - " + report.getTotalScore());
                getLog().info(
                        "-----------------------------------------------------------------------------------------");

                if (expectedScore > 0 && report.getTotalScore().doubleValue() < expectedScore) {
                    throw new MojoFailureException("CyBench score is less than expected:"
                            + report.getTotalScore().doubleValue() + " < " + expectedScore);
                }

                String reportEncrypted = ReportingService.getInstance().prepareReportForDelivery(securityBuilder,
                        report);
                reportsFolder = PluginUtils.checkReportSaveLocation(reportsFolder);
                String deviceReports = null;
                String resultURL = null;
                Map<?, ?> response = new HashMap<>();
                if (report.isEligibleForStoringExternally() && shouldSendReportToCyBench) {
                    String tokenAndEmail = ComputationUtils.getRequestHeader(benchAccessToken, email);

                    String responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted,
                            tokenAndEmail, benchQueryToken);
                    if (StringUtils.isNotEmpty(responseWithUrl)) {
                        response = JSONUtils.parseJsonIntoMap(responseWithUrl);
                    }
                    if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
                        deviceReports = String.valueOf(response.get(Constants.REPORT_USER_URL));
                        resultURL = String.valueOf(response.get(Constants.REPORT_URL));
                        isReportSentSuccessFully = true;
                        report.setDeviceReportsURL(deviceReports);
                        report.setReportURL(resultURL);
                    }
                } else {
                    getLog().info("You may submit your report '"
                            + IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE) + "' manually at "
                            + Constants.CYB_UPLOAD_URL);
                }

                String reportJSON = JSONUtils.marshalToPrettyJson(report);
                // getLog().info(reportJSON);
                if (shouldStoreReportToFileSystem) {
                    String fileNameForReport;
                    String fileNameForReportEncrypted;
                    fileNameForReport = ComputationUtils.createFileNameForReport(reportName, start,
                            report.getTotalScore(), false);
                    fileNameForReportEncrypted = ComputationUtils.createFileNameForReport(reportName, start,
                            report.getTotalScore(), true);

                    getLog().info("Saving test results to '" + IOUtils.getReportsPath(reportsFolder, fileNameForReport)
                            + "'");
                    IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, fileNameForReport), reportJSON);
                    getLog().info("Saving encrypted test results to '"
                            + IOUtils.getReportsPath(reportsFolder, fileNameForReportEncrypted) + "'");
                    IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, fileNameForReportEncrypted),
                            reportEncrypted);
                }
                getLog().info("Removing all temporary auto-generated files....");
                IOUtils.removeTestDataFiles();
                getLog().info("Removed all temporary auto-generated files!!!");

                if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
                    getLog().info("Benchmark report submitted successfully to " + Constants.REPORT_URL);
                    getLog().info("You can find all device benchmarks on " + deviceReports);
                    getLog().info("Your report is available at " + resultURL);
                    getLog().info("NOTE: It may take a few minutes for your report to appear online");

                    if (response.containsKey("automatedComparisons")) {
                        List<Map<String, Object>> automatedComparisons = (List<Map<String, Object>>) response
                                .get("automatedComparisons");
                        if (BenchmarkRunner.tooManyAnomalies(automatedComparisons)) {
                            System.exit(1);
                        }
                    }
                } else {
                    String errMsg = BenchmarkRunner.getErrorResponseMessage(response);
                    if (errMsg != null) {
                        getLog().error("CyBench backend service sent error response: " + errMsg);
                    }
                    getLog().info("You may submit your report '"
                            + IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE) + "' manually at "
                            + Constants.CYB_UPLOAD_URL);
                }
            } catch (Throwable t) {
                getLog().error(t);
                if (t.getMessage() != null && t.getMessage().contains("/META-INF/BenchmarkList")) {
                    getLog().info("-------------------No benchmark tests found-------------------");
                } else {
                    throw new MojoExecutionException("Error during benchmarks run", t);
                }
            } finally {
                DeliveryService.getInstance().close();

                getLog().info(
                        "-----------------------------------------------------------------------------------------");
                getLog().info("         Finished CyBench benchmarking ("
                        + ComputationUtils.formatInterval(System.currentTimeMillis() - start) + ")");
                getLog().info(
                        "-----------------------------------------------------------------------------------------");
            }

            if (!isReportSentSuccessFully && shouldSendReportToCyBench && shouldFailBuildOnReportDeliveryFailure) {
                throw new MojoExecutionException(
                        "Error during benchmarks run, report was not sent to CyBench as configured!");
            }
        } else {
            getLog().info("Skipping CyBench execution");
        }
    }

    public ComparisonConfig checkConfigValidity() throws Exception {
        ComparisonConfig verifiedComparisonConfig = new ComparisonConfig();

        String SCOPE_STR = automationScope;
        if (StringUtils.isBlank(SCOPE_STR)) {
            throw new Exception("Scope is not specified!");
        } else {
            SCOPE_STR = SCOPE_STR.toUpperCase();
        }
        ComparisonConfig.Scope SCOPE;
        String COMPARE_VERSION = automationCompareVersion;
        Integer NUM_LATEST_REPORTS = automationNumLatestReports;
        Integer ANOMALIES_ALLOWED = automationAnomaliesAllowed;
        String METHOD_STR = automationMethod;
        if (StringUtils.isBlank(METHOD_STR)) {
            throw new Exception("Method is not specified!");
        } else {
            METHOD_STR = METHOD_STR.toUpperCase();
        }
        ComparisonConfig.Method METHOD;
        String THRESHOLD_STR = automationThreshold;
        if (StringUtils.isNotBlank(THRESHOLD_STR)) {
            THRESHOLD_STR = THRESHOLD_STR.toUpperCase();
        }
        ComparisonConfig.Threshold THRESHOLD;
        Double PERCENT_CHANGE_ALLOWED = automationPercentChangeAllowed;
        Double DEVIATIONS_ALLOWED = automationDeviationsAllowed;

        if (NUM_LATEST_REPORTS != null) {
            if (NUM_LATEST_REPORTS < 1) {
                throw new Exception("Not enough latest reports specified to compare to!");
            }
            verifiedComparisonConfig.setCompareLatestReports(NUM_LATEST_REPORTS);
        } else {
            throw new Exception("Number of latest reports to compare to was not specified!");
        }
        if (ANOMALIES_ALLOWED != null) {
            if (ANOMALIES_ALLOWED < 1) {
                throw new Exception("Not enough anomalies allowed specified!");
            }
            verifiedComparisonConfig.setAnomaliesAllowed(ANOMALIES_ALLOWED);
        } else {
            throw new Exception("Anomalies allowed was not specified!");
        }

        if (!EnumUtils.isValidEnum(ComparisonConfig.Scope.class, SCOPE_STR)) {
            throw new Exception("Scope is invalid!");
        } else {
            SCOPE = ComparisonConfig.Scope.valueOf(SCOPE_STR);
            verifiedComparisonConfig.setScope(SCOPE);
        }
        if (!EnumUtils.isValidEnum(ComparisonConfig.Method.class, METHOD_STR)) {
            throw new Exception("Method is invalid!");
        } else {
            METHOD = ComparisonConfig.Method.valueOf(METHOD_STR);
            verifiedComparisonConfig.setMethod(METHOD);
        }

        if (SCOPE.equals(ComparisonConfig.Scope.WITHIN) && StringUtils.isNotEmpty(COMPARE_VERSION)) {
            COMPARE_VERSION = "";
            getLog().warn(
                    "Automated comparison config scoped specified as WITHIN but compare version was also specified, will compare WITHIN the currently tested version.");
        } else if (SCOPE.equals(ComparisonConfig.Scope.BETWEEN) && StringUtils.isBlank(COMPARE_VERSION)) {
            throw new Exception("Scope specified as BETWEEN but no compare version specified!");
        } else if (SCOPE.equals(ComparisonConfig.Scope.BETWEEN)) {
            verifiedComparisonConfig.setCompareVersion(COMPARE_VERSION);
        }

        if (METHOD.equals(ComparisonConfig.Method.SD)) {
            if (DEVIATIONS_ALLOWED != null) {
                if (DEVIATIONS_ALLOWED <= 0) {
                    throw new Exception("Method specified as SD but not enough deviations allowed were specified!");
                }
                verifiedComparisonConfig.setDeviationsAllowed(DEVIATIONS_ALLOWED);
            } else {
                throw new Exception("Method specified as SD but deviations allowed was not specified!");
            }
        } else if (METHOD.equals(ComparisonConfig.Method.DELTA)) {
            if (!EnumUtils.isValidEnum(ComparisonConfig.Threshold.class, THRESHOLD_STR)
                    || StringUtils.isBlank(THRESHOLD_STR)) {
                throw new Exception("Method specified as DELTA but no threshold specified or threshold is invalid!");
            } else {
                THRESHOLD = ComparisonConfig.Threshold.valueOf(THRESHOLD_STR);
                verifiedComparisonConfig.setThreshold(THRESHOLD);
            }

            if (THRESHOLD.equals(ComparisonConfig.Threshold.PERCENT_CHANGE)) {
                if (PERCENT_CHANGE_ALLOWED != null) {
                    if (PERCENT_CHANGE_ALLOWED <= 0) {
                        throw new Exception(
                                "Threshold specified as PERCENT_CHANGE but percent change is not high enough!");
                    }
                    verifiedComparisonConfig.setPercentChangeAllowed(PERCENT_CHANGE_ALLOWED);
                } else {
                    throw new Exception(
                            "Threshold specified as PERCENT_CHANGE but percent change allowed was not specified!");
                }
            }
        }

        return verifiedComparisonConfig;
    }
}
