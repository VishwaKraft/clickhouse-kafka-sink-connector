package com.vishwakraft.clickhouse.sink.connector.metadata;

import static java.time.Instant.from;
import static java.time.Instant.ofEpochMilli;

import java.time.*;

/**
 * This class holds constants that define upper and lower bounds for
 * various date and time data types used in ClickHouse, particularly
 * Date32, DateTime, and DateTime64.
 */
public class DataTypeRange {

    // Set clickhouse-jdbc limits

    /**
     * Minimum supported value for Date32 in ClickHouse.
     */
    public static final Integer CLICKHOUSE_MIN_SUPPORTED_DATE32 =(int) LocalDate.of(1925, 1, 1).toEpochDay();

    /**
     * Maximum supported value for Date32 in ClickHouse.
     */
    public static final Integer CLICKHOUSE_MAX_SUPPORTED_DATE32 =(int) LocalDate.of(2283, 11, 11).toEpochDay();;

    /**
     * Maximum epoch second for DateTime64, corresponding to
     * 2299-12-31 23:59:59 UTC.
     */
    public static final long DATETIME64_MAX = LocalDateTime
            .of(LocalDate.of(2299, 12, 31), LocalTime.MAX)
            .toEpochSecond(ZoneOffset.UTC);

    /**
     * Minimum epoch second for DateTime64, corresponding to
     * 1900-01-01 00:00:00 UTC.
     */
    public static final long DATETIME64_MIN = LocalDateTime
            .of(LocalDate.of(1900, 1, 1), LocalTime.MIN)
            .toEpochSecond(ZoneOffset.UTC);

    /**
     * Minimum {@link Instant} for supported DateTime64 in ClickHouse,
     * derived from {@code DATETIME64_MIN}.
     */
    public static final Instant CLICKHOUSE_MIN_SUPPORTED_DATETIME64 =
            from(ofEpochMilli(DATETIME64_MIN * 1000)
                    .atZone(ZoneId.of("UTC")))
                    .plusNanos(DATETIME64_MIN * 1000 % 1_000);

    /**
     * Maximum {@link Instant} for supported DateTime64 in ClickHouse,
     * derived from {@code DATETIME64_MAX}.
     */
    public static final Instant CLICKHOUSE_MAX_SUPPORTED_DATETIME64 =
            from(ofEpochMilli(DATETIME64_MAX * 1000)
                    .atZone(ZoneId.of("UTC"))
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59)
                    .withNano(000000));

    // DateTime and DateTime32

    /**
     * Minimum epoch second for DateTime32 in ClickHouse.
     */
    public static final long DATETIME32_MIN = 0L;

    /**
     * Maximum epoch second for DateTime32 in ClickHouse, corresponding
     * to 2106-02-07 06:28:15 UTC.
     */
    public static final long DATETIME32_MAX = LocalDateTime
            .of(LocalDate.of(2106, 2, 7), LocalTime.of(6, 28, 15))
            .toEpochSecond(ZoneOffset.UTC);

    // DateTime

    /**
     * Default DateTime minimum value in ClickHouse: 1900-01-01 00:00:00.0
     */
    public static final String DATETIME_MIN = "1900-01-01 00:00:00.0";

    /**
     * Default DateTime maximum value in ClickHouse: 2299-12-31 23:59:59.0
     */
    public static final String DATETIME_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_1_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_2_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_3_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_4_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_5_MAX = "2299-12-31 23:59:59.0";

    /**
     * Additional maximum reference for DateTime with microsecond
     * scale. Same as {@code DATETIME_MAX}.
     */
    public static final String DATETIME_6_MAX = "2299-12-31 23:59:59.0";

    /**
     * Maximum reference for DateTime64(6) in ClickHouse, referencing
     * 2299-12-31 23:59:59.0
     */
    public static final String DATETIME64_6_MAX = "2299-12-31 23:59:59.0";
}
