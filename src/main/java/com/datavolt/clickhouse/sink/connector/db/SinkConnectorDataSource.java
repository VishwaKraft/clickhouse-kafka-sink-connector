package com.vishwakraft.clickhouse.sink.connector.db;

import com.github.housepower.jdbc.ClickHouseConnection;
import com.github.housepower.jdbc.BalancedClickhouseDataSource;

import java.sql.SQLException;
import java.util.Properties;

/**
 * The SinkConnectorDataSource class wraps the BalancedClickhouseDataSource class
 * to provide custom behavior for obtaining connections to ClickHouse.
 * <p>
 * This class is used in the ClickHouse sink connector to manage the creation
 * and configuration of database connections. It can be extended or modified
 * to use custom HTTP clients or handle other specific connection logic if required.
 * </p>
 */
public class SinkConnectorDataSource {

    private final BalancedClickhouseDataSource dataSource;

    /**
     * Constructs a new SinkConnectorDataSource with the specified URL and properties.
     * <p>
     * This constructor initializes the underlying BalancedClickhouseDataSource
     * with the provided URL and properties.
     * </p>
     *
     * @param url the URL of the ClickHouse server to connect to.
     * @param properties the properties to configure the connection.
     * @throws SQLException if an error occurs while creating the data source.
     */
    public SinkConnectorDataSource(String url, Properties properties) throws SQLException {
        this.dataSource = new BalancedClickhouseDataSource(url, properties);
    }

    /**
     * Returns a connection to the ClickHouse server.
     * <p>
     * This method delegates the connection request to the underlying
     * BalancedClickhouseDataSource instance.
     * </p>
     *
     * @return a ClickHouseConnection object.
     * @throws SQLException if an error occurs while obtaining the connection.
     */
    public ClickHouseConnection getConnection() throws SQLException {
        // Custom behavior can be added here if needed, for example, logging or monitoring.
        return dataSource.getConnection();
    }

    public BalancedClickhouseDataSource getDataSource() {
        return this.dataSource;
    }
}
