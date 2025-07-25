This file, ClickHouseSinkTask.java, is part of a Kafka Connect sink connector implementation for ingesting data into ClickHouse, a columnar database. It extends the `SinkTask` class provided by Kafka Connect and defines the behavior of the task responsible for processing records from Kafka topics and writing them to ClickHouse.

### Key Responsibilities of the File:
1. **Initialization (`start` method):**
   - Configures the task using the provided configuration map.
   - Parses a mapping of Kafka topics to ClickHouse tables.
   - Sets up a queue (`LinkedBlockingQueue`) to hold batches of records.
   - Initializes a `ClickHouseBatchExecutor` to schedule and execute batch processing tasks.
   - Creates a `DeDuplicator` to handle duplicate record detection.

2. **Processing Records (`put` method):**
   - Receives a collection of `SinkRecord` objects from Kafka Connect.
   - Uses a `ClickHouseConverter` to convert Kafka records into a format suitable for ClickHouse (`ClickHouseStruct`).
   - De-duplicates records using the `DeDuplicator`.
   - Adds the processed records to the queue for ingestion into ClickHouse.

3. **Managing Offsets (`preCommit` method):**
   - Handles offset management by returning the offsets that Kafka Connect should commit.
   - Ensures that offsets are properly tracked and committed to Kafka.

4. **Task Lifecycle Management:**
   - `start`: Initializes resources and starts the task.
   - `stop`: Shuts down the executor and cleans up resources.
   - `open` and `close`: Handles partition-specific initialization and cleanup.

5. **Batch Execution:**
   - Uses a `ClickHouseBatchRunnable` to process batches of records from the queue and write them to ClickHouse.
   - The `ClickHouseBatchExecutor` schedules this runnable at fixed intervals.

6. **Versioning (`version` method):**
   - Returns the version of the task, typically matching the connector version.

### Key Components:
- **`ClickHouseBatchExecutor`**: Manages the scheduling and execution of batch processing tasks.
- **`DeDuplicator`**: Ensures duplicate records are not processed.
- **`ClickHouseConverter`**: Converts Kafka records into a format suitable for ClickHouse.
- **`LinkedBlockingQueue`**: Holds batches of records to be processed.

### Purpose:
This file defines the core logic for a Kafka Connect sink task that ingests data from Kafka topics into ClickHouse. It handles configuration, record processing, batching, scheduling, and offset management, ensuring reliable and efficient data ingestion.