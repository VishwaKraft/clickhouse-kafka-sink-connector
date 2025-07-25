package com.vishwakraft.clickhouse.sink.connector.db.operations;

import com.vishwakraft.clickhouse.sink.connector.converters.ClickHouseDataTypeMapper;
import com.vishwakraft.clickhouse.sink.connector.data.ClickHouseDataType;

import io.debezium.data.VariableScaleDecimal;
import io.debezium.time.MicroTimestamp;
import io.debezium.time.Timestamp;
import io.debezium.time.ZonedTimestamp;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides base operations to handle ClickHouse table creation and data-type
 * mapping. This class contains logic to map Kafka Connect {@link Schema}
 * fields to ClickHouse data types.
 */
public class ClickHouseTableOperationsBase {

    /**
     * The schema parameter key for scale.
     */
    public static final String SCALE = "scale";

    /**
     * The schema parameter key for precision in decimal types.
     */
    public static final String PRECISION = "connect.decimal.precision";

    /**
     * Default precision for decimal columns.
     */
    private static final int DEFAULT_PRECISION = 10;

    /**
     * Default scale for decimal columns.
     */
    private static final int DEFAULT_SCALE = 2;

    /**
     * String constant for default Decimal(10,2) type.
     */
    private static final String DEFAULT_DECIMAL_TYPE = "Decimal("
            + DEFAULT_PRECISION + "," + DEFAULT_SCALE + ")";

    /**
     * String constant for Decimal(64,18) type used by variable scale decimals.
     */
    private static final String DECIMAL_64_18 = "Decimal(64,18)";

    /**
     * String constant for DateTime64(3) type (millisecond precision).
     */
    private static final String DATETIME64_3 = "DateTime64(3)";

    /**
     * String constant for DateTime64(6) type (microsecond precision).
     */
    private static final String DATETIME64_6 = "DateTime64(6)";

    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(
            ClickHouseTableOperationsBase.class.getName());

    /**
     * Default constructor.
     */
    public ClickHouseTableOperationsBase() {
        // No initialization needed here.
    }

    /**
     * Generates a mapping from column names to ClickHouse data types based on
     * a provided array of Kafka Connect {@link Field} objects. Handles special
     * cases like Decimal, DateTime64, and arrays.
     *
     * @param fields An array of {@link Field} representing schema fields.
     * @return A map where the key is the column name and the value is the
     *         corresponding ClickHouse data type as a String.
     */
    public Map<String, String> getColumnNameToCHDataTypeMapping(Field[] fields) {
        ClickHouseDataTypeMapper mapper = new ClickHouseDataTypeMapper();
        Map<String, String> columnToDataTypesMap = new HashMap<>();

        for (Field f : fields) {
            String colName = f.name();
            Schema.Type type = f.schema().type();
            String schemaName = f.schema().name();

            if (type == Schema.Type.ARRAY) {
                schemaName = f.schema().valueSchema().type().name();
                ClickHouseDataType dt = mapper.getClickHouseDataType(
                        f.schema().valueSchema().type(), null);
                columnToDataTypesMap.put(
                        colName,
                        "Array(" + dt.name() + ")"
                );
                continue;
            }
            // Input:
            ClickHouseDataType dataType =
                    mapper.getClickHouseDataType(type, schemaName);

            if (dataType != null) {
                if (dataType == ClickHouseDataType.Decimal) {
                    // Get Scale, precision from parameters.
                    Map<String, String> params = f.schema().parameters();

                    // Postgres numeric data type has no scale/precision.
                    if (schemaName.equalsIgnoreCase(
                            VariableScaleDecimal.LOGICAL_NAME)) {
                        columnToDataTypesMap.put(
                                colName,
                                DECIMAL_64_18
                        );
                        continue;
                    }

                    if (params != null
                            && params.containsKey(SCALE)
                            && params.containsKey(PRECISION)) {
                        columnToDataTypesMap.put(
                                colName,
                                "Decimal(" + params.get(PRECISION) + ","
                                        + params.get(SCALE) + ")"
                        );
                    } else {
                        columnToDataTypesMap.put(
                                colName,
                                DEFAULT_DECIMAL_TYPE
                        );
                    }
                } else if (dataType == ClickHouseDataType.DateTime64) {
                    // Timestamp (with milliseconds scale),
                    // DATETIME, DATETIME(0 -3) -> DateTime64(3)
                    if (f.schema().type() == Schema.INT64_SCHEMA.type()
                            && f.schema().name().equalsIgnoreCase(
                            Timestamp.SCHEMA_NAME)) {
                        columnToDataTypesMap.put(colName, DATETIME64_3);
                    } else if (
                            (f.schema().type() == Schema.INT64_SCHEMA.type()
                                    && f.schema().name().equalsIgnoreCase(
                                    MicroTimestamp.SCHEMA_NAME))
                                    || (f.schema().type() == Schema.STRING_SCHEMA.type()
                                    && f.schema().name().equalsIgnoreCase(
                                    ZonedTimestamp.SCHEMA_NAME))
                    ) {
                        // MicroTimestamp (with microseconds precision),
                        // DATETIME(3 -6) -> DateTime64(6)
                        // TIMESTAMP(1..6) -> ZONEDTIMESTAMP(Debezium)
                        // -> DateTime64(6)
                        columnToDataTypesMap.put(colName, DATETIME64_6);
                    } else {
                        columnToDataTypesMap.put(colName, dataType.name());
                    }
                } else {
                    columnToDataTypesMap.put(colName, dataType.name());
                }
            } else {
                log.info(" **** DATA TYPE MAPPING not found: TYPE:"
                        + type.getName() + "SCHEMA NAME:" + schemaName);
            }
        }
        return columnToDataTypesMap;
    }
}
