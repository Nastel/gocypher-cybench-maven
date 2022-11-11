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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.lang3.BooleanUtils;
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
import org.openjdk.jmh.runner.options.TimeValue;

import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JMHUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.core.utils.SecurityUtils;
import com.gocypher.cybench.launcher.BenchmarkRunner;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.model.BenchmarkingContext;
import com.gocypher.cybench.launcher.model.TooManyAnomaliesException;
import com.gocypher.cybench.launcher.plugin.utils.PluginUtils;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.services.ConfigurationHandler;
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
	@Parameter(property = "cybench.benchmarkModes", defaultValue = "Throughput")
	private String benchmarkModes = "Throughput";
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
	@Parameter(property = "cybench.automationNumLatestReports", defaultValue = "-1")
	private int automationNumLatestReports;
	@Parameter(property = "cybench.automationAnomaliesAllowed", defaultValue = "-1")
	private int automationAnomaliesAllowed;
	@Parameter(property = "cybench.automationMethod", defaultValue = "")
	private String automationMethod;
	@Parameter(property = "cybench.automationThreshold", defaultValue = "")
	private String automationThreshold;
	@Parameter(property = "cybench.automationPercentChangeAllowed", defaultValue = "-1")
	private double automationPercentChangeAllowed;
	@Parameter(property = "cybench.automationDeviationsAllowed", defaultValue = "-1")
	private double automationDeviationsAllowed;

	static boolean validConfigFile;

	@Override
	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException {
		if (!skip && System.getProperty(PluginUtils.KEY_SKIP_CYBENCH) == null) {
			System.setProperty("collectHw", "true");
			long start = System.currentTimeMillis();
			getLog().info("-----------------------------------------------------------------------------------------");
			getLog().info("                        Starting CyBench benchmarks (Maven Plugin)                       ");
			getLog().info("-----------------------------------------------------------------------------------------");

			ComparisonConfig automatedComparisonCfg;
			try {
				automatedComparisonCfg = checkConfigValidity();
				getLog().info("** Configuration loaded: automated comparison configuration");
			} catch (Exception e) {
				automatedComparisonCfg = null;
				getLog().error("Failed to parse automated comparison configuration", e);
			}

			BenchmarkingContext benchContext = new BenchmarkingContext();
			benchContext.setStartTime(start);
			benchContext.setBenchSource(benchSource);
			benchContext.setAutomatedComparisonCfg(automatedComparisonCfg);

			try {
				PluginUtils.resolveAndUpdateClasspath(getLog(), project, getPluginContext(), classpathScope);

				initContext(benchContext);

				if (StringUtils.isEmpty(reportName)) {
					reportName = MessageFormat.format("Benchmark for {0}:{1}:{2}", project.getGroupId(),
							project.getArtifactId(), project.getVersion());
				}

				benchContext.getProjectMetadata().put(Constants.PROJECT_NAME, project.getArtifactId());
				benchContext.getProjectMetadata().put(Constants.PROJECT_VERSION, project.getVersion());

				getLog().info("Executing benchmarks...");

				analyzeBenchmarkClasses(benchContext);
				buildOptions(benchContext);

				Map<String, Object> benchmarkSettings = setConfiguration(benchContext);

				Collection<RunResult> results = runBenchmarks(benchContext);

				getLog().info("Benchmark finished, executed tests count: " + results.size());

				BenchmarkOverviewReport report = processResults(benchContext, benchmarkSettings, results);
				sendReport(benchContext, report);
			} catch (TooManyAnomaliesException e) {
				throw new MojoExecutionException("Too many anomalies found during benchmarks run: " + e.getMessage());
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

			Boolean reportSentSuccessfully = (Boolean) benchContext.getContextMetadata("reportSentSuccessfully");
			if (!BooleanUtils.toBoolean(reportSentSuccessfully) && shouldSendReportToCyBench
					&& shouldFailBuildOnReportDeliveryFailure) {
				throw new MojoExecutionException(
						"Error during benchmarks run, report was not sent to CyBench as configured!");
			}
		} else {
			getLog().info("Skipping CyBench execution");
		}
	}

	public Map<String, Object> setConfiguration(BenchmarkingContext benchContext) {
		Map<String, Object> benchmarkSettings = new HashMap<String, Object>();

		benchmarkSettings.put("benchSource", benchSource);
		benchmarkSettings.put("benchWarmUpIteration", warmUpIterations);
		benchmarkSettings.put("benchWarmUpSeconds", warmUpTime);
		benchmarkSettings.put("benchMeasurementIteration", measurementIterations);
		benchmarkSettings.put("benchMeasurementSeconds", measurementTime);
		benchmarkSettings.put("benchForkCount", forks);
		benchmarkSettings.put("benchThreadCount", threads);
		benchmarkSettings.put("benchModes", benchmarkModes);
		benchmarkSettings.put("benchReportName", reportName);

		return benchmarkSettings;

	}

	public void initContext(BenchmarkingContext benchContext) {
		getLog().info("Collecting hardware, software information...");
		benchContext.setHWProperties(CollectSystemInformation.getEnvironmentProperties());
		getLog().info("Collecting JVM properties...");
		benchContext.setJVMProperties(CollectSystemInformation.getJavaVirtualMachineProperties());

		benchContext.setDefaultBenchmarksMetadata(ComputationUtils.parseBenchmarkMetadata(customBenchmarkMetadata));

		Properties tempProps = ConfigurationHandler.loadConfiguration("/config/", Constants.LAUNCHER_CONFIGURATION);
		getLog().info("tempProps: " + tempProps.toString());
		if (!tempProps.isEmpty()) { // can add config validation here.
			overrideConfiguration(tempProps);
			benchContext.setConfiguration(tempProps);
			validConfigFile = true;
			getLog().info("Overriding configuration based on file.");
		}

	}

	// Override pom.xml/default JMH properties
	public void overrideJMHConfiguration(Properties props) {

		if (checkExistsAndNotNull(props, "warmUpIterations")) {
			warmUpIterations = Integer.parseInt(props.getProperty("warmUpIterations"));
		}
		if (checkExistsAndNotNull(props, "measurementIterations")) {
			measurementIterations = Integer.parseInt(props.getProperty("measurementIterations"));
		}
		if (checkExistsAndNotNull(props, "warmUpSeconds")) {
			warmUpTime = Integer.parseInt(props.getProperty("warmUpSeconds"));
		}
		if (checkExistsAndNotNull(props, "numberOfBenchmarkForks")) {
			forks = Integer.parseInt(props.getProperty("numberOfBenchmarkForks"));
		}
		if (checkExistsAndNotNull(props, "runThreadCount")) {
			threads = Integer.parseInt(props.getProperty("runThreadCount"));
		}
		if (checkExistsAndNotNull(props, "benchmarkModes")) {
			benchmarkModes = (String) props.getProperty("benchmarkModes");
		}
		if (checkExistsAndNotNull(props, "measurementSeconds")) {
			measurementTime = Integer.parseInt(props.getProperty("measurementSeconds"));
		}
	}

	// Override pom.xml/default properties with cybench-launcher.properties
	public void overrideConfiguration(Properties props) {
		if (checkExistsAndNotNull(props, "sendReport")) {
			shouldSendReportToCyBench = Boolean.parseBoolean(props.getProperty("sendReport"));
		}
		if (checkExistsAndNotNull(props, "reportName")) {
			reportName = (String) props.getProperty("reportName");
		}
		if (checkExistsAndNotNull(props, "benchAccessToken")) {
			benchAccessToken = props.getProperty("benchAccessToken");

		}
		if (checkExistsAndNotNull(props, "benchQueryToken")) {
			benchQueryToken = props.getProperty("benchAccessToken");
		}
		if (checkExistsAndNotNull(props, "emailAddress")) {
			email = props.getProperty("emailAddress");

		}
		if (checkExistsAndNotNull(props, "reportUploadStatus")) {
			reportUploadStatus = props.getProperty("reportUploadStatus");

		}
		overrideJMHConfiguration(props);

	}

	public void buildOptions(BenchmarkingContext benchContext) {
		Options opt;
		if (validConfigFile) {
			try {
				getLog().info("Building options via config file..");
				BenchmarkRunner.buildOptions(benchContext);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (useCyBenchBenchmarkSettings && !validConfigFile) {
			ChainedOptionsBuilder chainedOptionsBuilder = benchContext.getOptBuilder().forks(forks)
					.measurementTime(TimeValue.seconds(measurementTime)).measurementIterations(measurementIterations)
					.warmupIterations(warmUpIterations).warmupTime(TimeValue.seconds(warmUpTime)).threads(threads)
					.shouldDoGC(true).addProfiler(GCProfiler.class)
					// .addProfiler(HotspotThreadProfiler.class) //obsolete
					// .addProfiler(HotspotRuntimeProfiler.class) //obsolete
					.addProfiler(SafepointsProfiler.class);
			if (jmvArgs.length() > 0) {
				chainedOptionsBuilder.jvmArgs(jmvArgs);
			} else {
				chainedOptionsBuilder.detectJvmArgs();
			}
			opt = chainedOptionsBuilder.build();
			benchContext.setOptions(opt);

		} else {
			opt = benchContext.getOptBuilder().shouldDoGC(true).addProfiler(GCProfiler.class)
					// .addProfiler(HotspotThreadProfiler.class) //obsolete
					// .addProfiler(HotspotRuntimeProfiler.class) //obsolete
					.addProfiler(SafepointsProfiler.class).detectJvmArgs().build();
			benchContext.setOptions(opt);
		}
	}

	public void analyzeBenchmarkClasses(BenchmarkingContext benchContext) {
		benchContext.setSecurityBuilder(new SecurityBuilder());

		List<String> benchmarkNames = JMHUtils.getAllBenchmarkClasses();
		for (String benchmarkClass : benchmarkNames) {
			try {
				Class<?> classObj = Class.forName(benchmarkClass);
				SecurityUtils.generateMethodFingerprints(classObj, benchContext.getManualFingerprints(),
						benchContext.getClassFingerprints());
				SecurityUtils.computeClassHashForMethods(classObj, benchContext.getGeneratedFingerprints());
			} catch (ClassNotFoundException exc) {
				getLog().error("Class not found in the classpath for execution", exc);
			}
		}
	}

	public Collection<RunResult> runBenchmarks(BenchmarkingContext benchContext) throws Exception {
		Runner runner = new Runner(benchContext.getOptions());
		Collection<RunResult> results = runner.run();
		benchContext.getResults().addAll(results);
		return results;
	}

	public BenchmarkOverviewReport processResults(BenchmarkingContext benchContext,
			Map<String, Object> benchmarkSettings, Collection<RunResult> results) {
		BenchmarkOverviewReport report;
		List<BenchmarkReport> benchReports;
		if (benchContext.getReport() == null) {
			report = ReportingService.getInstance().createBenchmarkReport(results,
					benchContext.getDefaultBenchmarksMetadata());
			benchContext.setReport(report);
			benchReports = report.getBenchmarksList();

			report.getEnvironmentSettings().put("environment", benchContext.getHWProperties());
			report.getEnvironmentSettings().put("jvmEnvironment", benchContext.getJVMProperties());
			report.getEnvironmentSettings().put("unclassifiedProperties",
					CollectSystemInformation.getUnclassifiedProperties());
			report.getEnvironmentSettings().put("userDefinedProperties",
					ComputationUtils.customUserDefinedProperties(userProperties));

			ComparisonConfig automatedComparisonCfg = benchContext.getAutomatedComparisonCfg();
			if (automatedComparisonCfg != null) {
				if (automatedComparisonCfg.getScope().equals(ComparisonConfig.Scope.WITHIN)) {
					automatedComparisonCfg
							.setCompareVersion(benchContext.getProjectMetadata(Constants.PROJECT_VERSION));
				}
				automatedComparisonCfg.setRange(String.valueOf(automatedComparisonCfg.getCompareLatestReports()));
				automatedComparisonCfg.setProjectName(benchContext.getProjectMetadata(Constants.PROJECT_NAME));
				automatedComparisonCfg.setProjectVersion(benchContext.getProjectMetadata(Constants.PROJECT_VERSION));
				report.setAutomatedComparisonConfig(automatedComparisonCfg);
				getLog().info("Set auto comparison.");
			}
		} else {
			report = benchContext.getReport();
			benchReports = ReportingService.getInstance().updateBenchmarkReport(report, results,
					benchContext.getDefaultBenchmarksMetadata());
		}
		report.setBenchmarkSettings(benchmarkSettings);

		for (BenchmarkReport benchmarkReport : benchReports) {
			String name = benchmarkReport.getName();
			benchmarkReport.setClassFingerprint(benchContext.getClassFingerprints().get(name));
			benchmarkReport.setGeneratedFingerprint(benchContext.getGeneratedFingerprints().get(name));
			benchmarkReport.setManualFingerprint(benchContext.getManualFingerprints().get(name));
			try {
				JMHUtils.ClassAndMethod classAndMethod = new JMHUtils.ClassAndMethod(name).invoke();
				String clazz = classAndMethod.getClazz();
				String method = classAndMethod.getMethod();
				getLog().info("Adding metadata for benchmark: " + clazz + " test: " + method);
				Class<?> aClass = Class.forName(clazz);
				Optional<Method> benchmarkMethod = JMHUtils.getBenchmarkMethod(method, aClass);
				BenchmarkRunner.appendMetadataFromAnnotated(benchmarkMethod, benchmarkReport);
				BenchmarkRunner.appendMetadataFromAnnotated(Optional.of(aClass), benchmarkReport);
				syncReportsMetadata(benchContext, report, benchmarkReport);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		return report;
	}

	private void completeReport(BenchmarkingContext benchContext, BenchmarkOverviewReport report) throws Exception {
		if (report.hasBenchmarks()) {
			List<BenchmarkReport> customBenchmarksCategoryCheck = report.getBenchmarks().get("CUSTOM");
			report.getBenchmarks().remove("CUSTOM");
			for (BenchmarkReport benchReport : customBenchmarksCategoryCheck) {
				report.addToBenchmarks(benchReport);
			}
			report.computeScores();
			report.updateUploadStatus(reportUploadStatus);
		}

		getLog().info("-----------------------------------------------------------------------------------------");
		getLog().info(" Report score - " + report.getTotalScore());
		getLog().info("-----------------------------------------------------------------------------------------");

		if (expectedScore > 0 && report.getTotalScore().doubleValue() < expectedScore) {
			throw new MojoFailureException("CyBench score is less than expected:" + report.getTotalScore().doubleValue()
					+ " < " + expectedScore);
		}

		report.setTimestamp(System.currentTimeMillis());
		report.setTimestampUTC(ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli());
	}

	@SuppressWarnings("unchecked")
	private void sendReport(BenchmarkingContext benchContext, BenchmarkOverviewReport report) throws Exception {
		completeReport(benchContext, report);

		String reportEncrypted = ReportingService.getInstance()
				.prepareReportForDelivery(benchContext.getSecurityBuilder(), report);
		reportsFolder = PluginUtils.checkReportSaveLocation(reportsFolder);
		String deviceReports = null;
		String resultURL = null;
		Map<?, ?> response = new HashMap<>();
		if (report.isEligibleForStoringExternally() && shouldSendReportToCyBench) {
			String tokenAndEmail = ComputationUtils.getRequestHeader(benchAccessToken, email);

			try (DeliveryService ds = DeliveryService.getInstance()) {
				String responseWithUrl = ds.sendReportForStoring(reportEncrypted, tokenAndEmail, benchQueryToken);
				if (StringUtils.isNotEmpty(responseWithUrl)) {
					response = JSONUtils.parseJsonIntoMap(responseWithUrl);
				}
				if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
					deviceReports = String.valueOf(response.get(Constants.REPORT_USER_URL));
					resultURL = String.valueOf(response.get(Constants.REPORT_URL));
					benchContext.getContextMetadata().put("reportSentSuccessfully", true);
					report.setDeviceReportsURL(deviceReports);
					report.setReportURL(resultURL);
				}
			} catch (Exception exc) {
				getLog().error("Failed to send report over delivery service\n" + exc);
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
			fileNameForReport = ComputationUtils.createFileNameForReport(reportName, benchContext.getStartTime(),
					report.getTotalScore(), false);
			fileNameForReportEncrypted = ComputationUtils.createFileNameForReport(reportName,
					benchContext.getStartTime(), report.getTotalScore(), true);

			getLog().info("Saving test results to '" + IOUtils.getReportsPath(reportsFolder, fileNameForReport) + "'");
			IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, fileNameForReport), reportJSON);
			getLog().info("Saving encrypted test results to '"
					+ IOUtils.getReportsPath(reportsFolder, fileNameForReportEncrypted) + "'");
			IOUtils.storeResultsToFile(IOUtils.getReportsPath(reportsFolder, fileNameForReportEncrypted),
					reportEncrypted);
		}
		getLog().info("Removing all temporary auto-generated files....");
		IOUtils.removeTestDataFiles();
		getLog().info("Removed all temporary auto-generated files!!!");

		if (!response.isEmpty() && report.getUploadStatus().equals(Constants.REPORT_PRIVATE)) {
			getLog().error(
					"*** Total Reports allowed in repository: " + response.get(Constants.REPORTS_ALLOWED_FROM_SUB));
			getLog().error("*** Total Reports in repository: " + response.get(Constants.NUM_REPORTS_IN_REPO));
		}

		if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
			getLog().info("Benchmark report submitted successfully to " + Constants.REPORT_URL);
			getLog().info("You can find all device benchmarks on " + deviceReports);
			getLog().info("Your report is available at " + resultURL);
			getLog().info("NOTE: It may take a few minutes for your report to appear online");

			if (response.containsKey("automatedComparisons")) {
				List<Map<String, Object>> automatedComparisons = (List<Map<String, Object>>) response
						.get("automatedComparisons");
				BenchmarkRunner.verifyAnomalies(automatedComparisons);
			}
		} else {
			String errMsg = BenchmarkRunner.getErrorResponseMessage(response);
			if (errMsg != null) {
				getLog().error("CyBench backend service sent error response: " + errMsg);
			}
			if (BenchmarkRunner.getAllowedToUploadBasedOnSubscription(response)) {
				// user was allowed to upload report, and there was still an error
				getLog().info("You may submit your report '"
						+ IOUtils.getReportsPath(reportsFolder, Constants.CYB_REPORT_CYB_FILE) + "' manually at "
						+ Constants.CYB_UPLOAD_URL);
			}
		}
	}

	public void syncReportsMetadata(BenchmarkingContext benchContext, BenchmarkOverviewReport report,
			BenchmarkReport benchmarkReport) {
		try {
			String projectVersion = benchContext.getProjectMetadata(Constants.PROJECT_VERSION);
			String projectArtifactId = benchContext.getProjectMetadata(Constants.PROJECT_NAME);

			if (StringUtils.isNotEmpty(benchmarkReport.getProject())) {
				report.setProject(benchmarkReport.getProject());
			} else {
				getLog().info("* Project name metadata not defined, grabbing it from build files...");
				report.setProject(projectArtifactId);
				benchmarkReport.setProject(projectArtifactId);
			}

			if (StringUtils.isNotEmpty(benchmarkReport.getProjectVersion())) {
				report.setProjectVersion(benchmarkReport.getProjectVersion());
			} else {
				getLog().info("* Project version metadata not defined, grabbing it from build files...");
				report.setProjectVersion(projectVersion); // default
				benchmarkReport.setProjectVersion(projectVersion);
			}

			if (StringUtils.isEmpty(benchmarkReport.getVersion())) {
				benchmarkReport.setVersion(projectVersion);
			}

			if (StringUtils.isEmpty(report.getBenchmarkSessionId())) {
				String sessionId = null;
				Map<String, String> bMetadata = benchmarkReport.getMetadata();
				if (bMetadata != null) {
					sessionId = bMetadata.get("benchSession");
				}

				if (StringUtils.isEmpty(sessionId)) {
					sessionId = UUID.randomUUID().toString();
				}

				report.setBenchmarkSessionId(sessionId);
			}

			if (benchmarkReport.getCategory().equals("CUSTOM")) {
				int classIndex = benchmarkReport.getName().lastIndexOf(".");
				if (classIndex > 0) {
					String pckgAndClass = benchmarkReport.getName().substring(0, classIndex);
					int pckgIndex = pckgAndClass.lastIndexOf(".");
					if (pckgIndex > 0) {
						String pckg = pckgAndClass.substring(0, pckgIndex);
						benchmarkReport.setCategory(pckg);
					} else {
						benchmarkReport.setCategory(pckgAndClass);
					}
				}
			}
		} catch (Exception e) {
			getLog().error("Error while attempting to synchronize benchmark metadata from runner: ", e);
		}
	}

	public ComparisonConfig checkConfigValidity() throws Exception {
		ComparisonConfig verifiedComparisonConfig = new ComparisonConfig();

		getLog().info("Verifying config validity.");
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
		getLog().info("Found method as: " + METHOD_STR);
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

		if (NUM_LATEST_REPORTS != null && NUM_LATEST_REPORTS != -1) {
			if (NUM_LATEST_REPORTS < 1) {
				throw new Exception("Not enough latest reports specified to compare to!");
			}
			verifiedComparisonConfig.setCompareLatestReports(NUM_LATEST_REPORTS);
		} else {
			throw new Exception("Number of latest reports to compare to was not specified!");
		}
		if (ANOMALIES_ALLOWED != null && ANOMALIES_ALLOWED != -1) {
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
			if (DEVIATIONS_ALLOWED != null && DEVIATIONS_ALLOWED != -1) {
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
				if (PERCENT_CHANGE_ALLOWED != null && PERCENT_CHANGE_ALLOWED != -1) {
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

	public boolean checkExistsAndNotNull(Properties props, String key) {
		try {
			if (props.containsKey(key)) {
				if (!props.getProperty(key).isBlank()) {
					return true;
				}
			}
		} catch (Exception e) {
			System.out.println("Error checking property validity.");
			System.out.println(e);
		}
		return false;
	}
}
