# gocypher-cybench-maven

## CyBench launcher plugin integation into any Maven project

```xml
<plugin>
    <groupId>com.gocypher.cybench.launcher.plugin</groupId>
    <artifactId>gocypher-cybench-launch-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
		<execution>
			<phase>test</phase>
			<goals>
				<goal>cybench</goal>
            </goals>
        </execution>
    </executions>
	<configuration>
        <skip>false</skip>
        <forks>1</forks>
        <threads>1</threads>
        <measurementIterations>5</measurementIterations>
        <warmUpIterations>1</warmUpIterations>
        <warmUpSeconds>4</warmUpSeconds>
        <expectedScore>1</expectedScore>
        <shouldSendReportToCyBench>false</shouldSendReportToCyBench>
        <shouldStoreReportToFileSystem>true</shouldStoreReportToFileSystem>
        <reportUploadStatus>private</reportUploadStatus>
        <reportsFolder>C:/CyBench/reports/</reportsFolder>
        <reportName>My Private Build Process Benchmark</reportName>
		<customBenchmarkMetadata>com.gocypher.benchmarks.client.CollectionsBenchmarks=category:Collections;</customBenchmarkMetadata>
        <customProperties>project=My Test Project;</customProperties>
    </configuration>
</plugin>
```			