package com.vishwakraft.clickhouse.sink.connector.db.operations;

import com.github.housepower.data.type.complex.DataTypeArray;
import com.vishwakraft.clickhouse.sink.connector.db.DBMetadata;
import com.google.common.annotations.VisibleForTesting;
import org.apache.kafka.connect.data.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.vishwakraft.clickhouse.sink.connector.db.ClickHouseDbConstants.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wraps all functionality related to creating tables from Kafka sink
 * records.
 *
 * <p>This class auto-generates the SQL for table creation and executes
 * the query to create a new table in ClickHouse.
 */
public class ClickHouseAutoCreateTable
        extends ClickHouseTableOperationsBase {

    /**
     * Logger instance for the ClickHouseAutoCreateTable class.
     */
    private static final Logger log = LoggerFactory.getLogger(
            ClickHouseAutoCreateTable.class.getName());

    /**
     * Creates a new ClickHouse table using the provided fields and primary
     * key.
     *
     * <p>This method builds the CREATE TABLE query based on the provided
     * fields and configuration flags, logs the query, and executes it.
     *
     * @param primaryKey an ArrayList of primary key columns
     * @param tableName the name of the table to create
     * @param databaseName the name of the database in which the table is
     *                     to be created
     * @param fields an array of fields from the Kafka sink record
     * @param connection a JDBC Connection to the ClickHouse database
     * @param isNewReplacingMergeTree flag indicating the new engine type
     * @param useReplicatedReplacingMergeTree flag indicating use of a
     *                                        replicated engine
     * @param rmtDeleteColumn the column name for the delete flag; if null,
     *                        a default is used
     * @throws SQLException if a SQL exception occurs during table creation
     */
    public void createNewTable(ArrayList<String> primaryKey, String tableName,
                               String databaseName, Field[] fields,
                               Connection connection,
                               boolean isNewReplacingMergeTree,
                               boolean useReplicatedReplacingMergeTree,
                               String rmtDeleteColumn)
            throws SQLException {
        Map<String, String> colNameToDataTypeMap =
                this.getColumnNameToCHDataTypeMapping(fields);
        String createTableQuery = this.createTableSyntax(primaryKey, tableName,
                databaseName, fields, colNameToDataTypeMap,
                isNewReplacingMergeTree, useReplicatedReplacingMergeTree,
                rmtDeleteColumn);
        log.info(String.format("**** AUTO CREATE TABLE for database(%s), "
                + "Query :%s)", databaseName, createTableQuery));
        // TODO: Run this before a session is created.
        DBMetadata metadata = new DBMetadata();
        metadata.executeSystemQuery(connection, createTableQuery);
    }

    /**
     * Generates the CREATE TABLE SQL syntax for ClickHouse.
     *
     * <p>The SQL is built based on the provided map of column names to data
     * types, along with the specified engine flags.
     *
     * <pre>
     * CREATE TABLE database.`table_name`
     *   ( `col1` data_type1, `col2` data_type2, ... )
     *   Engine=ReplacingMergeTree(version_column)
     *   PRIMARY KEY(col1) ORDER BY(col1)
     * </pre>
     *
     * @param primaryKey a list of primary key columns
     * @param tableName the name of the table to create
     * @param databaseName the name of the database
     * @param fields an array of Kafka Connect fields
     * @param columnToDataTypesMap a map of column names to ClickHouse
     *                             data types
     * @param isNewReplacingMergeTreeEngine flag for new engine type usage
     * @param useReplicatedReplacingMergeTree flag for using replicated engine
     * @param rmtDeleteColumn the deletion column name; if null or empty, a
     *                        default is used
     * @return a SQL string for creating the table
     */
    public String createTableSyntax(ArrayList<String> primaryKey,
                                    String tableName, String databaseName, Field[] fields,
                                    Map<String, String> columnToDataTypesMap,
                                    boolean isNewReplacingMergeTreeEngine,
                                    boolean useReplicatedReplacingMergeTree,
                                    String rmtDeleteColumn) {

        StringBuilder createTableSyntax = new StringBuilder();

        createTableSyntax.append(CREATE_TABLE).append(" ")
                .append(databaseName).append(".")
                .append("`").append(tableName).append("`");
        if (useReplicatedReplacingMergeTree == true) {
            createTableSyntax.append(" ON CLUSTER `{cluster}` ");
        }

        createTableSyntax.append("(");

        for (Field f : fields) {
            String colName = f.name();
            String dataType = columnToDataTypesMap.get(colName);
            boolean isNull = false;
            if (f.schema().isOptional() == true) {
                isNull = true;
            }
            createTableSyntax.append("`").append(colName).append("`")
                    .append(" ").append(dataType);

            // Ignore setting NULL/NOT NULL for JSON and Array types.
            if (isNull) {
                createTableSyntax.append(" ").append(NULL);
            } else {
                createTableSyntax.append(" ").append(NOT_NULL);
            }
            createTableSyntax.append(",");
        }

        String isDeletedColumn = IS_DELETED_COLUMN;
        if (rmtDeleteColumn != null && !rmtDeleteColumn.isEmpty()) {
            isDeletedColumn = rmtDeleteColumn;
        }

        if (isNewReplacingMergeTreeEngine == true) {
            createTableSyntax.append("`").append(VERSION_COLUMN)
                    .append("` ").append(VERSION_COLUMN_DATA_TYPE)
                    .append(",");
            createTableSyntax.append("`").append(isDeletedColumn)
                    .append("` ").append(IS_DELETED_COLUMN_DATA_TYPE);
        } else {
            // Append sign and version columns.
            createTableSyntax.append("`").append(SIGN_COLUMN)
                    .append("` ").append(SIGN_COLUMN_DATA_TYPE)
                    .append(",");
            createTableSyntax.append("`").append(VERSION_COLUMN)
                    .append("` ").append(VERSION_COLUMN_DATA_TYPE);
        }
        createTableSyntax.append(")");
        createTableSyntax.append(" ");

        if (isNewReplacingMergeTreeEngine == true) {
            if (useReplicatedReplacingMergeTree == true) {
                createTableSyntax.append(String.format(
                        "Engine=ReplicatedReplacingMergeTree(%s, %s)",
                        VERSION_COLUMN, isDeletedColumn));
            } else {
                createTableSyntax.append(" Engine=ReplacingMergeTree(")
                        .append(VERSION_COLUMN).append(",")
                        .append(isDeletedColumn).append(")");
            }
        } else {
            if (useReplicatedReplacingMergeTree == true) {
                createTableSyntax.append(String.format(
                        "Engine=ReplicatedReplacingMergeTree(%s)",
                        VERSION_COLUMN));
            } else {
                createTableSyntax.append("ENGINE = ReplacingMergeTree(")
                        .append(VERSION_COLUMN).append(")");
            }
        }
        createTableSyntax.append(" ");

        if (primaryKey != null
                && isPrimaryKeyColumnPresent(primaryKey, columnToDataTypesMap)) {
            createTableSyntax.append(PRIMARY_KEY).append("(");
            createTableSyntax.append(primaryKey.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(",")));
            createTableSyntax.append(") ");
            createTableSyntax.append(ORDER_BY).append("(");
            createTableSyntax.append(primaryKey.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(",")));
            createTableSyntax.append(")");
        } else {
            // TODO: Define a default ORDER BY clause.
            createTableSyntax.append(ORDER_BY_TUPLE);
        }
        return createTableSyntax.toString();
    }

    /**
     * Checks if all primary key columns are present in the column-to-data
     * type map.
     *
     * @param primaryKeys a list of primary key column names
     * @param columnToDataTypesMap a map of column names to data types
     * @return true if all primary key columns are present; false otherwise
     */
    @VisibleForTesting
    boolean isPrimaryKeyColumnPresent(ArrayList<String> primaryKeys,
                                      Map<String, String> columnToDataTypesMap) {
        for (String primaryKey : primaryKeys) {
            if (!columnToDataTypesMap.containsKey(primaryKey)) {
                return false;
            }
        }
        return true;
    }
}
