package com.vishwakraft.clickhouse.sink.connector;

import com.vishwakraft.clickhouse.sink.connector.common.Utils;
import com.vishwakraft.clickhouse.sink.connector.common.Version;
import com.vishwakraft.clickhouse.sink.connector.converters.ClickHouseConverter;
import com.vishwakraft.clickhouse.sink.connector.deduplicator.DeDuplicator;
import com.vishwakraft.clickhouse.sink.connector.executor.ClickHouseBatchExecutor;
import com.vishwakraft.clickhouse.sink.connector.executor.ClickHouseBatchRunnable;
import com.vishwakraft.clickhouse.sink.connector.model.ClickHouseStruct;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * <p>Creates sink service instance, takes records loaded from those Kafka
 * partitions and ingests them into ClickHouse via the Sink service.</p>
 * <p>This class extends {@link SinkTask} and is managed by Kafka Connect.</p>
 */
public class ClickHouseSinkTask extends SinkTask {

    /**
     * The logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(
            ClickHouseSinkTask.class);

    /**
     * Initial delay (in milliseconds) for scheduling the batch runnable.
     */
    private static final long INITIAL_DELAY_MS = 0L;

    /**
     * The unique task identifier.
     */
    private String id = "-1";

    /**
     * Executor responsible for scheduling and running batches of records
     * to be ingested into ClickHouse.
     */
    private ClickHouseBatchExecutor executor;

    /**
     * Queue used to store batches of records to be processed.
     */
    private LinkedBlockingQueue<List<ClickHouseStruct>> records;

    /**
     * A de-duplicator utility to detect and skip duplicate messages.
     */
    private DeDuplicator deduplicator;

    /**
     * The configuration for the sink connector.
     */
    private ClickHouseSinkConnectorConfig config;

    /**
     * The total number of records processed by this task.
     */
    private long totalRecords;

    /**
     * Default constructor.
     */
    public ClickHouseSinkTask() {
        // No specific initialization here.
    }

    /**
     * Starts the task by initializing the configuration, setting up the
     * executor for batching and scheduling, and creating the de-duplicator.
     *
     * @param config The task configuration map provided by Kafka Connect.
     */
    @Override
    public void start(Map<String, String> config) {
        //ToDo: Check buffer.count.records and how its used.
        //final long count = Long.parseLong(config.get(ClickHouseSinkConnectorConfigVariables.BUFFER_COUNT));
        //log.info("start({}):{}", this.id, count);
        log.info("start({})", this.id);

        this.config = new ClickHouseSinkConnectorConfig(config);

        Map<String, String> topic2TableMap = null;
        try {
            topic2TableMap = Utils.parseTopicToTableMap(
                    this.config.getString(
                            ClickHouseSinkConnectorConfigVariables
                                    .CLICKHOUSE_TOPICS_TABLES_MAP.toString()));
        } catch (Exception e) {
            log.info("Error parsing topic to table map" + e);
        }

        this.id = "task-" + this.config.getLong(
                ClickHouseSinkConnectorConfigVariables.TASK_ID.toString());

        int maxQueueSize = this.config.getInt(
                ClickHouseSinkConnectorConfigVariables.MAX_QUEUE_SIZE.toString());

        this.records = new LinkedBlockingQueue<>(maxQueueSize);

        ClickHouseBatchRunnable runnable = new ClickHouseBatchRunnable(
                this.records, this.config, topic2TableMap);

        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Sink Connector thread-pool-%d")
                .build();

        this.executor = new ClickHouseBatchExecutor(
                this.config.getInt(
                        ClickHouseSinkConnectorConfigVariables.THREAD_POOL_SIZE
                                .toString()),
                namedThreadFactory);

        this.executor.scheduleAtFixedRate(
                runnable,
                INITIAL_DELAY_MS,
                this.config.getLong(
                        ClickHouseSinkConnectorConfigVariables.BUFFER_FLUSH_TIME
                                .toString()),
                TimeUnit.MILLISECONDS);

        this.deduplicator = new DeDuplicator(this.config);
    }

    /**
     * Stops the task by shutting down the executor, preventing further
     * processing of records.
     */
    @Override
    public void stop() {
        log.info("stop({})", this.id);
        if (this.executor != null) {
            this.executor.shutdown();
        }
    }

    /**
     * Called when the task is opened, typically to initialize or resume
     * processing for the given set of partitions.
     *
     * @param partitions The collection of topic partitions that
     *                   will be processed by this task.
     */
    @Override
    public void open(final Collection<TopicPartition> partitions) {
        log.info("open({}):{}", this.id, partitions.size());
    }

    /**
     * Called when the task is closed, typically to clean up resources
     * associated with the given partitions.
     *
     * @param partitions The collection of topic partitions that
     *                   were processed by this task.
     */
    @Override
    public void close(final Collection<TopicPartition> partitions) {
        log.info("close({}):{}", this.id, partitions.size());
    }

    /**
     * Receives a collection of {@link SinkRecord} objects from Kafka Connect,
     * processes them (optionally de-duplicating), and places them into the
     * queue for ingestion into ClickHouse.
     *
     * @param records The collection of records to process.
     */
    @Override
    public void put(Collection<SinkRecord> records) {
        totalRecords += records.size();

        long taskId = this.config.getLong(
                ClickHouseSinkConnectorConfigVariables.TASK_ID.toString());

        log.debug("******** CLICKHOUSE received records **** {} Task Id: {}",
                totalRecords, taskId);

        ClickHouseConverter converter = new ClickHouseConverter();
        List<ClickHouseStruct> batch = new ArrayList<>();

        for (SinkRecord record : records) {
            if (this.deduplicator.isNew(record.topic(), record)) {
                ClickHouseStruct c = converter.convert(record);
                if (c != null) {
                    batch.add(c);
                }
                //Update the hashmap with the topic name and the list of records.
            }
        }

        try {
            this.records.put(batch);
        } catch (InterruptedException e) {
            throw new RetriableException(e);
        }
    }

    //    private void appendToRecords(Map<String, List<ClickHouseStruct>> convertedRecords) {
    //        ConcurrentLinkedQueue<List<ClickHouseStruct>> structs;
    //
    //        synchronized (this.records) {
    //            //Iterate through convertedRecords and add to the records map.
    //            for (Map.Entry<String, List<ClickHouseStruct>> entry : convertedRecords.entrySet()) {
    //                if (this.records.containsKey(entry.getKey())) {
    //                    structs = this.records.get(entry.getKey());
    //                    structs.add(entry.getValue());
    //
    //                } else {
    //                    structs = new ConcurrentLinkedQueue<>();
    //                    structs.add(entry.getValue());
    //                }
    //                this.records.put(entry.getKey(), structs);
    //
    //            }
    //        }
    //    }

    /**
     * <p>preCommit() is something like a replacement for flush - it takes the
     * same parameters. Returns the offsets that Kafka Connect should commit.
     * Typical behavior is to call flush and return the same offsets that
     * were passed, which means Kafka Connect should commit all the offsets
     * it passed to the connector via preCommit.</p>
     * <p>If this method returns an empty set of offsets, then Kafka Connect
     * will record no offsets at all.</p>
     * <p>If the connector is going to handle all offsets in the external
     * system and doesn't need Kafka Connect to record anything, then this
     * method should be overridden (instead of flush) and return an empty
     * set of offsets.</p>
     *
     * @param currentOffsets A map of topic partition to current offset.
     * @return A map of committed offsets that Kafka Connect should record.
     * @throws RetriableException If there's a retriable error.
     */
    @Override
    public Map<TopicPartition, OffsetAndMetadata> preCommit(
            Map<TopicPartition, OffsetAndMetadata> currentOffsets)
            throws RetriableException {

        log.info("preCommit({}) {}", this.id, currentOffsets.size());

        Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                new HashMap<>();

        try {
            currentOffsets.forEach((topicPartition, offsetAndMetadata) -> {
                committedOffsets.put(topicPartition,
                        new OffsetAndMetadata(offsetAndMetadata.offset()));
            });
        } catch (Exception e) {
            log.info("preCommit({}):{}", this.id, e.getMessage());
            return new HashMap<>();
        }

        return committedOffsets;
    }

    //  @Override
    //  public void flush(Map<TopicPartition, OffsetAndMetadata> currentOffsets) {
    //      // No-op. The connector is managing the offsets.
    //      if(!this.config.getBoolean(ClickHouseSinkConnectorConfigVariables.ENABLE_KAFKA_OFFSET)) {
    //          return currentOffsets;
    //      }
    //  }

    /**
     * Returns the version of this task, typically the same as the connector
     * version.
     *
     * @return The version string of this task.
     */
    @Override
    public String version() {
        return Version.VERSION;
    }
}
