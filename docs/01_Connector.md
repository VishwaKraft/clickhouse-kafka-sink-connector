# ClickHouse Sink Connector

## Flow of Execution

The flow of execution for the `ClickHouseSinkConnector` class is as follows:

### 1. Initialization
- The `ClickHouseSinkConnector` class is instantiated.
- The constructor (`ClickHouseSinkConnector()`) initializes the `ready` flag to `false` and logs the initialization.

---

### 2. Starting the Connector
- The `start(Map<String, String> conf)` method is called when the connector is started by Kafka Connect.
  - The provided configuration (`conf`) is stored in the `config` field.
  - The `ready` flag is set to `true`, indicating that the connector is ready to accept data.
  - Metrics are initialized using the `Metrics.initialize()` method, with configuration values for enabling metrics and the metrics endpoint port.

---

### 3. Task Management
- **Task Class Definition**:
  - The `taskClass()` method returns the `ClickHouseSinkTask` class, which defines the tasks that will handle data ingestion.
- **Task Configuration**:
  - The `taskConfigs(int maxTasks)` method is called to generate configurations for each task.
    - It waits until the `ready` flag is `true`.
    - For each task, it creates a configuration map by copying the main connector's configuration and adding a unique `TASK_ID` for the task.
    - A list of these configurations is returned.

---

### 4. Validation
- The `validate(Map<String, String> conf)` method is called to validate the connector's configuration.
  - It logs the validation process and validates the configuration using the parent class's `validate()` method.
  - (Optional) Additional validation for connectivity to the ClickHouse server and database/schema validity can be implemented.

---

### 5. Stopping the Connector
- The `stop()` method is called when the connector is stopped.
  - The `ready` flag is set to `false`, indicating that the connector is no longer ready to accept data.
  - Metrics are stopped using the `Metrics.stop()` method.

---

### 6. Configuration and Version
- **Configuration Definition**:
  - The `config()` method returns the configuration definition (`ConfigDef`) for the connector, which includes all the configurable properties.
- **Version**:
  - The `version()` method returns the current version of the connector.

---

### Summary of Execution Flow
1. **Initialization**: The connector is instantiated.
2. **Start**: The connector is started, configuration is stored, and metrics are initialized.
3. **Task Management**: Tasks are defined and configured for data ingestion.
4. **Validation**: Configuration is validated.
5. **Stop**: The connector is stopped, and metrics are cleaned up.
6. **Configuration and Version**: Configuration definitions and version information are provided.