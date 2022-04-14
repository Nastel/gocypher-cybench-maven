# GoCypher CyBench Maven plugin

[CyBench](https://cybench.io)  Maven plugin allows to run CyBench benchmark tests, generate report and send it to
CyBench during software build process.

Plugin is simply attached to project POM file and also can be used in continuous integration (CI) systems.

CyBench Maven plugin executes all classes which uses JMH framework for benchmark implementation and creates a report to
specified location at the end of benchmarking process. As CyBench report contains total score, so it is possible to
configure build failure if score does not pass the pre-defined threshold.

**Notice** that benchmarks are run on the server where software is built, so the builds machine must have enough HW
resources for a successful and stable benchmarking of software items.

## Start using CyBench Maven plugin

### Prerequisites for Usage

 * CyBench Plugin for Maven developed with:
    * Java 8 `jdk-1.8.0_311`
 * CyBench Plugin for Maven tested with:
    * Java 8
    * Java 11 `jdk-11.0.13`
    * Maven `v3.8.3`

**NOTE:** plugin runtime requires at least Java 8 and Maven `v3.2.5`

### CyBench Maven Plugin Usage

Add CyBench plugin tags in your project POM file under section `build -> plugins`. Set the execution parameters:

* execution phase `test`
* execution goal `cybench`.

```xml
<plugin>
    <groupId>com.gocypher.cybench.launcher.plugin</groupId>
    <artifactId>cybench-launcher-maven-plugin</artifactId>
    <version>1.0.5</version>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>cybench</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Prerequisites**

* A project must have dependencies to JMH framework and contain classes which implements benchmarks using JMH framework.

## Configuration

Plugin is configurable inside plugin configuration tags. Properties available for plugin behaviour configuration:

| Property name        | Description           | Default value  |
| ------------- |-------------| -----:|
| **forks**      | Number of forks for benchmark execution. |1 |
| **threads**      | Number of threads for each benchmark test.      |  1 |
| **measurementIterations**| Number of iterations for each benchmark.      |    5 |
| **measurementTime**| Time (in seconds) used for measurement execution (applies only for benchmarks where mode is throughput).     |    10 |
| **warmUpIterations**| Number of iterations for each benchmark warm-up.      |    3 |
| **warmUpTime**| Time (in seconds) used for warm-up execution (applies only for benchmarks where mode is throughput).     |    5 |
| **expectedScore**| Threshold for a total score. If report total score is lower then build fails.  |    -1 |
| **shouldSendReportToCyBench**| A boolean flag which indicates if the benchmark report should be sent to CyBench.  |    false |
| **shouldStoreReportToFileSystem** | A boolean flag which indicates if the benchmark report should be saved to file system | true |
| **reportsFolder**| Location in a local file system where reports shall be stored.  |    Current execution directory. |
| **reportUploadStatus**| Parameter which indicates if the report is public or private. Possible values: `public`, `private`  |   public  |
| **reportName**| Name of the benchmark report. |   CyBench Report  |
| **customBenchmarkMetadata**| A property which adds extra properties to the benchmarks report such as category or version or context. Configuration pattern is `<fully qualified benchmark class name>=<key1>:<value1>;<key2>:<value2>`. Example which adds category for class CollectionsBenchmarks: `com.gocypher.benchmarks.client.CollectionsBenchmarks=category:Collections;`   |   -  |
| **userProperties**| User defined properties which will be added to benchmarks report section `environmentSettings->userDefinedProperties` as key/value strings. Configuration pattern:`<key1>:<value1>;<key2>:<value2>`. Example which adds a library name:`library=My Library;` |   -  |
| **skip**| A flag which allows to skip benchmarks execution during build process. Benchmarks execution also can be skipped via JVM system property `-DskipCybench`. |   false  |
| **benchAccessToken** | By providing the "bench" token that you get after creating a workspace in CyBench UI, you can send reports to your private directory, which will be visible only to the users that you authorize. | - |
| **benchQueryToken** | By providing the "query" token that you get after creating a workspace in CyBench UI, you can run and send automated comparisons within your project to your private directory, which will be visible only to the users that you authorize. | - |
| **email** | Email property is used to identify report sender while sending reports to both private and public repositories | - |
| **shouldFailBuildOnReportDeliveryFailure**| A flag which triggers build failure if the benchmark report was configured to be sent to CyBench but its delivery failed. |   false |

You can also add a configuration for automated performance regression testing, which will run with every single
benchmark report.

**NOTE** In order to run automated comparisons, you must add the **benchQueryToken** to the configuration.

| Property name        | Description           | Options  |
| ------------- |-------------| -----:|
| **automationScope** | Choose between comparing within current version, or between previous versions. When using `BETWEEN`, a specific version must be specified with the property `automationCompareVersion`. | `WITHIN` or `BETWEEN` |
| **automationCompareVersion** | Used for `BETWEEN` version comparisons. | Any project version you have previously tested |
| **automationNumLatestReports** | How many reports do you want to compare against? 1 will compare this report against the most recent report in the version you are comparing against. # > 1 will compare this report against the average of the scores of the most recent # reports in the version you are comparing against. | Number >= 1 |
| **automationAnomaliesAllowed** | How many anomalies do you want to allow? If the number of benchmark anomalies surpasses your specified number, CyBench benchmark runner will fail... triggering your CI/CD pipeline to halt. | Number >= 0 |
| **automationMethod** | Decide which method of comparison to use. `DELTA` will compare difference in score, and requires an additional property, `automationThreshold`. `SD` will do comparisons regarding standard deviation. `SD` requires an additional property as well, `automationDeviationsAllowed`. | `DELTA` or `SD` |
| **automationThreshold** | Only used with the `DELTA` method. `GREATER` will compare raw scores, `PERCENT_CHANGE` is used to measure the percent change of the score in comparison to previous scores. `PERCENT_CHANGE` requires an additional property: `automationPercentChangeAllowed`. | `GREATER` or `PERCENT_CHANGE` |
| **automationPercentChangeAllowed** | This argument is used when running assertions, makes sure your new score is within X percent of the previous scores you're comparing to. | Any Double value. |
| **automationDeviationsAllowed** | Used with assertions to check that the new score is within the given amount of deviations from the mean. (mean being calculated from the scores being compared to). | Any Double value. |

### Example of CyBench Maven plugin configuration

```xml
<plugin>
    <groupId>com.gocypher.cybench.launcher.plugin</groupId>
    <artifactId>cybench-launcher-maven-plugin</artifactId>
    <version>1.0.5</version>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>cybench</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <forks>1</forks>
        <threads>1</threads>
        <measurementIterations>5</measurementIterations>
        <measurementTime>5</measurementTime>
        <warmUpIterations>1</warmUpIterations>
        <warmUpTime>5</warmUpTime>
        <shouldSendReportToCyBench>false</shouldSendReportToCyBench>
        <reportsFolder>./reports/</reportsFolder>
        <reportName>My Report</reportName>
        <userProperties>library=My Library;</userProperties>
        <customBenchmarkMetadata>com.gocypher.benchmarks.client.CollectionsBenchmarks=category:Collections;</customBenchmarkMetadata>

        <automationScope>BETWEEN</automationScope>
        <automationCompareVersion>2.0</automationCompareVersion>
        <automationNumLatestReports>1</automationNumLatestReports>
        <automationAnomaliesAllowed>1</automationAnomaliesAllowed>
        <automationMethod>DETLA</automationMethod>
        <automationThreshold>PERCENT_CHANGE</automationThreshold>
        <automationPercentChangeAllowed>10</automationPercentChangeAllowed>
    </configuration>
</plugin>
```

#### Optional: gocypher-cybench-annotation For adding custom benchmark annotations @BenchmarkTag / @BenchmarkMetaData

Include dependency to annotation processor in your project `pom.xml` file before the jmh annotation processor.

**Notice:** First launch will generate the @BenchmarkTag annotations for benchmarks if they do not yet exist and fail
build.

```xml
    <dependency>
        <groupId>com.gocypher.cybench.client</groupId>
        <artifactId>gocypher-cybench-annotations</artifactId>
        <version>1.3.5</version>
    </dependency>
```

## More information on benchmarking your code

* [CyBench Benchmark samples](https://github.com/K2NIO/cybench-java-benchmarks)
* [Avoiding Benchmarking Pitfalls on the JVM](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html#:~:text=JMH%20is%20a%20Java%20harness,to%20unwanted%20virtual%20machine%20optimizations)
* [JMH - Java Microbenchmark Harness](http://tutorials.jenkov.com/java-performance/jmh.html)
* [Java Benchmarks with JMH](https://medium.com/swlh/java-benchmarks-with-jmh-a-preamble-285510a77dd2)
* [Microbenchmarking with Java](https://www.baeldung.com/java-microbenchmark-harness)

### CyBench Maven Plugin Manual Building

This step is required in order to use latest CyBench Maven plugin versions during build process until it and its
dependencies are not released to Central Maven repository.

**Prerequisites**

* Maven command line tools on local machine.

#### Build gocypher-cybench-runner project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-java) to local machine.
* Navigate to directory `gocypher-cybench-client`.
* Run command from the command line

```sh
     mvn clean install
```

* After successful run project JAR's are installed to local Maven repository.

#### Build cybench-launcher-maven-plugin project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-maven) to local machine.
* Navigate to directory `cybench-launch-maven-plugin`.
* Run command from the command line

```sh
     mvn clean install
```

* After successful run project JAR's are installed to local Maven repository.
