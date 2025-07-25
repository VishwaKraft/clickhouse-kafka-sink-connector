package com.vishwakraft.clickhouse.sink.connector.db;

import com.vishwakraft.clickhouse.sink.connector.model.KafkaMetaData;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.kafka.connect.data.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class responsible for generating raw queries for the ClickHouse JDBC library.
 * <p>
 * This class contains methods for constructing SQL INSERT queries using input
 * functions, ensuring proper handling of column names, data types, and Kafka
 * metadata. It also validates and formats columns to be inserted into ClickHouse
 * based on the given schema.
 * </p>
 */
public class QueryFormatter {

    /**
     * Logger instance used for logging messages in the QueryFormatter class.
     * <p>
     * This logger is initialized using LogManager to log events related to
     * query formatting and database operations, allowing for tracking and
     * troubleshooting.
     * </p>
     */
    private static final Logger log = LoggerFactory.getLogger(QueryFormatter.class);

    /**
     * Checks if a column is related to Kafka metadata.
     * <p>
     * Kafka metadata columns (such as topic, partition, offset, etc.) are special
     * columns that need to be handled differently when generating insert queries.
     * </p>
     *
     * @param colName the name of the column to check.
     * @return true if the column is Kafka metadata, false otherwise.
     */
    private boolean isKafkaMetaDataColumn(String colName) {
        for (KafkaMetaData metaDataColumn : KafkaMetaData.values()) {
            String metaDataColName = metaDataColumn.getColumn();
            if (metaDataColName.equalsIgnoreCase(colName)) {
                return true;
            }
        }
        return false;
    }

     /**
     * Escape quotes in given string.
     *
     * @param str   string
     * @param quote quote to escape
     * @return escaped string
     */
    public static String escape(String str, char quote) {
        if (str == null) {
            return str;
        }

        int len = str.length();
        StringBuilder sb = new StringBuilder(len + 10);

        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == quote || ch == '\\') {
                sb.append('\\');
            }
            sb.append(ch);
        }

        return sb.toString();
    }


    /**
     * Generates an INSERT SQL query using input functions for the specified table and fields.
     * <p>
     * This method constructs an INSERT query for inserting data into ClickHouse. It
     * ensures that Kafka metadata and raw data columns are included if specified in
     * the configuration. It also ensures the columns in the source schema match
     * the ones expected by ClickHouse.
     * </p>
     *
     * @param tableName the name of the ClickHouse table.
     * @param fields the list of fields from the source schema.
     * @param columnNameToDataTypeMap a map of column names to their corresponding data types.
     * @param includeKafkaMetaData flag indicating whether Kafka metadata columns should be included.
     * @param includeRawData flag indicating whether raw data should be included in the query.
     * @param rawDataColumn the name of the raw data column.
     * @param dbName the name of the database.
     * @return a MutablePair containing the generated INSERT query and a map of column names to their indices.
     */
    public MutablePair<String, Map<String, Integer>> getInsertQueryUsingInputFunction(
            String tableName, List<Field> fields,
            Map<String, String> columnNameToDataTypeMap,
            boolean includeKafkaMetaData,
            boolean includeRawData,
            String rawDataColumn, String dbName) {

        Map<String, Integer> colNameToIndexMap = new HashMap<>();
        int index = 1;

        StringBuilder colNamesDelimited = new StringBuilder();
        StringBuilder colNamesToDataTypes = new StringBuilder();
        StringBuilder valuesStringBuilder = new StringBuilder();

        if (fields == null) {
            log.info("getInsertQueryUsingInputFunction, fields empty");
            return null;
        }

        // Loop over each column to generate the insert query and map data types
        for (Map.Entry<String, String> entry : columnNameToDataTypeMap.entrySet()) {
            String sourceColumnName = entry.getKey();
            String sourceColumnNameWithBackTicks = "`" + entry.getKey() + "`";
            String dataType = escape(entry.getValue(), '\'');

            // Override data type if necessary
            if (ColumnOverrides.getColumnOverride(dataType) != null) {
                dataType = ColumnOverrides.getColumnOverride(dataType);
            }

            if (dataType != null) {
                // Check if the column is Kafka metadata
                if (isKafkaMetaDataColumn(sourceColumnName)) {
                    if (includeKafkaMetaData) {
                        colNamesDelimited.append(sourceColumnNameWithBackTicks).append(",");
                        valuesStringBuilder.append("?,");
                        colNamesToDataTypes.append(sourceColumnNameWithBackTicks).append(" ").append(dataType).append(",");
                        colNameToIndexMap.put(sourceColumnName, index++);
                    }
                } else if (sourceColumnName.equalsIgnoreCase(rawDataColumn)) {
                    if (includeRawData) {
                        colNamesDelimited.append(sourceColumnNameWithBackTicks).append(",");
                        valuesStringBuilder.append("?,");
                        colNamesToDataTypes.append(sourceColumnNameWithBackTicks).append(" ").append(dataType).append(",");
                        colNameToIndexMap.put(sourceColumnName, index++);
                    }
                } else {
                    colNamesDelimited.append(sourceColumnNameWithBackTicks).append(",");
                    valuesStringBuilder.append("?,");
                    colNamesToDataTypes.append(sourceColumnNameWithBackTicks).append(" ").append(dataType).append(",");
                    colNameToIndexMap.put(sourceColumnName, index++);
                }
            } else {
                log.info(String.format("Table Name: %s, Database: %s,  Column(%s) ignored",
                        tableName, dbName, sourceColumnNameWithBackTicks));
            }
        }

        // Remove the terminating commas
        removeTrailingComma(colNamesDelimited);
        removeTrailingComma(colNamesToDataTypes);
        removeTrailingComma(valuesStringBuilder);

        // Construct the full insert query
        String tableWithBackTicks = "`" + tableName + "`";
        String insertQuery = String.format("insert into %s(%s) VALUES(%s)",
                tableWithBackTicks, colNamesDelimited, valuesStringBuilder);

        // Return the query and column index map
        MutablePair<String, Map<String, Integer>> response = new MutablePair<>();
        response.left = insertQuery;
        response.right = colNameToIndexMap;

        return response;
    }

    /**
     * Removes the trailing comma from the StringBuilder if present.
     *
     * @param stringBuilder the StringBuilder to process.
     */
    private void removeTrailingComma(StringBuilder stringBuilder) {
        int lastIndex = stringBuilder.lastIndexOf(",");
        if (lastIndex != -1) {
            stringBuilder.deleteCharAt(lastIndex);
        }
    }

    /**
     * Generates an INSERT SQL query using input functions for the specified table and columns.
     * <p>
     * This method constructs an INSERT query based on the provided column names and data types.
     * </p>
     *
     * @param tableName the name of the ClickHouse table.
     * @param columnNameToDataTypeMap a map of column names to their corresponding data types.
     * @return the generated INSERT SQL query.
     */
    public String getInsertQueryUsingInputFunction(String tableName, Map<String, String> columnNameToDataTypeMap) {
        StringBuilder colNamesDelimited = new StringBuilder();
        StringBuilder colNamesToDataTypes = new StringBuilder();
        StringBuilder valuesStringBuilder = new StringBuilder();


        // Loop over each column to generate the insert query
        for (Map.Entry<String, String> entry : columnNameToDataTypeMap.entrySet()) {
            String columnName = "`" + entry.getKey() + "`";
            colNamesDelimited.append(columnName).append(",");
            valuesStringBuilder.append("?,");
            colNamesToDataTypes.append(columnName).append(" ").append(entry.getValue()).append(",");
        }

        // Remove the terminating commas
        removeTrailingComma(colNamesDelimited);
        removeTrailingComma(colNamesToDataTypes);
        removeTrailingComma(valuesStringBuilder);

        // Construct the full insert query
        String tableWithBackTicks = "`" + tableName + "`";
        return String.format("insert into %s(%s) VALUES(%s)", tableWithBackTicks, colNamesDelimited, valuesStringBuilder);
    }
}
