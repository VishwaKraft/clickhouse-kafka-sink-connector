The file ClickHouseConverter.java is responsible for converting Kafka records into a format suitable for processing and ingestion into ClickHouse, a columnar database. It interprets Change Data Capture (CDC) operations (such as create, read, update, delete, and truncate) and processes the "before" and "after" states of records.

### Key Responsibilities:
1. **CDC Operation Handling**:
   - The `CDC_OPERATION` enum defines the types of CDC operations (`CREATE`, `READ`, `UPDATE`, `DELETE`, `TRUNCATE`).
   - The `getOperation` method determines the CDC operation type for a given Kafka `SinkRecord`.

2. **Record Conversion**:
   - Converts Kafka `SinkRecord` or `SourceRecord` objects into a map structure using methods like `convertKey`, `convertValue`, and `convertRecord`.
   - Handles both the key and value schemas of Kafka records.

3. **Struct Conversion**:
   - Converts Kafka `Struct` objects into a map of field names and values using the `convertStruct` method.
   - Processes individual fields of the `Struct` and converts them based on their schema type using the `convertObject` method.

4. **ClickHouse-Specific Struct Creation**:
   - The `convert` method parses a `SinkRecord` and creates a `ClickHouseStruct` object, which encapsulates the relevant fields for ClickHouse ingestion.
   - The `readBeforeOrAfterSection` method extracts the "before" or "after" sections of a record, depending on the CDC operation.

5. **Error Handling and Logging**:
   - Logs errors and warnings for unsupported schema types or conversion failures.
   - Provides debug logs for the conversion process.

### Purpose:
This class acts as a bridge between Kafka records and ClickHouse by transforming the data into a format that ClickHouse can process. It ensures that CDC operations are correctly interpreted and that the data is structured appropriately for ingestion into ClickHouse tables.