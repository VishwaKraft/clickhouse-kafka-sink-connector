## Clickhouse Connector for Debezium

This connector is built on a native TCP/IP-based driver, ensuring high performance and efficient communication with ClickHouse databases. It is designed to work seamlessly with Debezium, enabling real-time data streaming and integration.

## Documentation

To understand how the connector works and how to use it, please refer to the following documentation files:

- [Connector Overview](docs/01_Connector.md)
- [Task Details](docs/02_Task.md)
- [Converter Information](docs/03_Convertor.md)

## Maven Dependency

To use this connector in your Maven project, add the following dependency to your `pom.xml`:

```xml
<!-- https://mvnrepository.com/artifact/com.vishwakraft.clickhouse/clickhouse-kafka-sink-connector -->
<dependency>
    <groupId>com.vishwakraft.clickhouse</groupId>
    <artifactId>clickhouse-kafka-sink-connector</artifactId>
    <version>${version.clickhouse-connector}</version>
</dependency>
```

## Throughput Benchmark

The connector is optimized for high throughput and low latency. Below is a placeholder for benchmark results. Please update this section with your own performance metrics as needed.

| Test Scenario                | Records/sec | Notes                |
|-----------------------------|-------------|----------------------|
| Example: Debezium to CH      | TBD         | Single node, default |
| Example: Batch insert (1M)   | TBD         | Batch size: 1000000     |

