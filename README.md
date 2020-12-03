# GoCypher CyBench Maven plugin

CyBench Maven plugin allows to run CyBench benchmark tests, generate report and send it to CyBench during software build process. Plugin is simply attached to project POM file and also can be used in continuous integration (CI) systems. CyBench Maven plugin executes all classes which uses JMH framework for benchmark implementation and creates a report to specified location at the end of benchmarking process. As CyBench report contains total score, so it is possible to configure build failure if score does not pass the pre-defined threshold.

Notice that benchmarks are run on the server where software is built, so the builds machine must have enough HW resources for a successful and stable benchmarking of software items.  

## CyBench Maven Plugin Usage

Add CyBench plugin tags in your project POM file under section `build -> plugins`. 
Set the execution parameters:
* execution phase `test` 
* execution goal `cybench`.

**Prerequisites**

* A project must have dependencies to JMH framework and contain classes which implements benchmarks using JMH framework.
* Until CyBench Maven plugin and its dependencies are not released to Central Maven repository must build `GoCypher CyBench Launch Maven Plugin` locally and install it to local Maven repository. See section [CyBench Maven Plugin Build](#cybench-maven-plugin-building) for details.

Plugin is configurable inside plugin configuration tags. 

Properties available for plugin behaviour configuration:

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
| **userProperties**| User defined properties which will be added to benchmarks report section `environmentSettings->userDefinedProperties` as key/value strings. Configuration pattern:`<key1>:<value1>;<key2>:<value2>`. Example which adds a project name:`project=My Test Project;` |   -  |
| **skip**| A flag which allows to skip benchmarks execution during build process. Benchmarks execution also can be skipped via JVM system property `-DskipCybench`. |   false  |
| **shouldFailBuildOnReportDeliveryFailure**| A flag which triggers build failure if the benchmark report was configured to be sent to CyBench but its delivery failed. |   false |

### Example of CyBench Maven plugin configuration

```xml
<plugin>
    <groupId>com.gocypher.cybench.launcher.plugin</groupId>
    <artifactId>gocypher-cybench-launch-maven-plugin</artifactId>
    <version>1.0.0</version>
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
        <warmUpTime>4</warmUpTime>
        <expectedScore>1</expectedScore>
        <shouldSendReportToCyBench>false</shouldSendReportToCyBench>
        <shouldStoreReportToFileSystem>true</shouldStoreReportToFileSystem>
        <reportUploadStatus>private</reportUploadStatus>
        <reportsFolder>C:/CyBench/reports/</reportsFolder>
        <reportName>My Private Build Process Benchmark</reportName>
        <userProperties>project=My Test Project;</userProperties>
        <customBenchmarkMetadata>com.gocypher.benchmarks.client.CollectionsBenchmarks=category:Collections;</customBenchmarkMetadata>		
    </configuration>
</plugin>
```

## CyBench Maven Plugin Binaries install for immediate use

Releases page contains CyBench Maven plugin and its dependencies binaries (packaged in a zip file), which are possible to install to local Maven repository and start using it from any project immediatelly (without need to build any CyBench projects).

**Prerequisites**

* Maven command line tools on local machine.

### Install CyBench Maven plugin binaries

Install CyBench Maven plugin binaries (subfolder in zip file `cybench-maven-plugin`) to local Maven repository using command:
```sh
mvn install:install-file -Dfile=gocypher-cybench-launch-maven-plugin-1.0.0.jar -DgroupId=com.gocypher.cybench.launcher.plugin -DartifactId=gocypher-cybench-launch-maven-plugin 
```
### Start using CyBench Maven plugin

Include dependecy to CyBench Maven plugin in your project `pom.xml` file as described in the chapters above and start using it.

## CyBench Maven Plugin Building

This step is required in order to use CyBench Maven plugin during build process until it and its dependencies are not released to Central Maven repository.

**Prerequisites**

* Maven command line tools on local machine.


### Build gocypher-cybench-runner project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-java) to local machine.
* Navigate to directory `gocypher-cybench-client`.
* Run command from the command line 
```sh
     mvn clean install
```
* After successful run project JAR's are installed to local Maven repository.

### Build  gocypher-cybench-launch-maven-plugin project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-maven) to local machine.
* Navigate to directory `Cybench-Launch-Maven-Plugin`.
* Run command from the command line 
```sh
     mvn clean install
```
* After successful run project JAR's are installed to local Maven repository.

