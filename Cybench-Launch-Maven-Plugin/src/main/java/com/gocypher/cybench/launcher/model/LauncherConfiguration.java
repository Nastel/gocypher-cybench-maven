package com.gocypher.cybench.launcher.model;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public class LauncherConfiguration {
    /** The forks count for all benchmarks. */
    @Parameter(property = "cybench.forks", defaultValue = "1")
    private int forks = 1;

    @Parameter(property = "cybench.threads", defaultValue = "1")
    private int threads = 1;

    /** The measurement iteration count for all benchmarks*/
    @Parameter(property = "cybench.measurementIterations", defaultValue = "3")
    private int measurementIterations = 3;

    /** The warm-up iteration count for all benchmarks*/
    @Parameter(property = "cybench.warmUpIterations", defaultValue = "1")
    private int warmUpIterations = 3;
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

    public String getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(String userProperties) {
        this.userProperties = userProperties;
    }

    public int getForks() {
        return forks;
    }

    public void setForks(int forks) {
            this.forks = forks;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMeasurementIterations() {
        return measurementIterations;
    }

    public void setMeasurementIterations(int measurementIterations) {
        this.measurementIterations = measurementIterations;
    }

    public int getWarmUpIterations() {
        return warmUpIterations;
    }

    public void setWarmUpIterations(int warmUpIterations) {
        this.warmUpIterations = warmUpIterations;
    }

    public int getWarmUpSeconds() {
        return warmUpSeconds;
    }

    public void setWarmUpSeconds(int warmUpSeconds) {
        this.warmUpSeconds = warmUpSeconds;
    }

    public double getExpectedScore() {
        return expectedScore;
    }

    public void setExpectedScore(double expectedScore) {
        this.expectedScore = expectedScore;
    }

    public boolean isShouldSendReportToCyBench() {
        return shouldSendReportToCyBench;
    }

    public void setShouldSendReportToCyBench(boolean shouldSendReportToCyBench) {
        this.shouldSendReportToCyBench = shouldSendReportToCyBench;
    }

    public boolean isShouldStoreReportToFileSystem() {
        return shouldStoreReportToFileSystem;
    }

    public void setShouldStoreReportToFileSystem(boolean shouldStoreReportToFileSystem) {
        this.shouldStoreReportToFileSystem = shouldStoreReportToFileSystem;
    }

    public String getReportUploadStatus() {
        return reportUploadStatus;
    }

    public void setReportUploadStatus(String reportUploadStatus) {
        this.reportUploadStatus = reportUploadStatus;
    }

    public String getReportsFolder() {
        return reportsFolder;
    }

    public void setReportsFolder(String reportsFolder) {
        this.reportsFolder = reportsFolder;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getUserBenchmarkMetadata() {
        return userBenchmarkMetadata;
    }

    public void setUserBenchmarkMetadata(String userBenchmarkMetadata) {
        this.userBenchmarkMetadata = userBenchmarkMetadata;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }
}
