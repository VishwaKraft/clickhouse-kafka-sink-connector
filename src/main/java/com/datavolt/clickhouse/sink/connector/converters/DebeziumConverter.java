package com.vishwakraft.clickhouse.sink.connector.converters;

import com.vishwakraft.clickhouse.sink.connector.data.ClickHouseDataType;
import com.vishwakraft.clickhouse.sink.connector.metadata.DataTypeRange;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Date;import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.TimeZone;

import static java.time.Instant.ofEpochMilli;

public class DebeziumConverter {

    private static final int MICROS_IN_SEC = 1000000;
    private static final int MICROS_IN_MILLI = 1000;
    private static final int U_INT16_MAX = 0xFFFF; // 65535
    private static final java.math.BigDecimal DECIMAL128_MAX = new java.math.BigDecimal("170141183460469231731687303715884105727");
    private static final java.math.BigDecimal DECIMAL128_MIN = new java.math.BigDecimal("-170141183460469231731687303715884105728");

    private static final Logger log = LoggerFactory.getLogger(DebeziumConverter.class);


    public static class MicroTimeConverter {
        /**
         * Function to convert Long(Epoch)
         * to Formatted String(Time)
         * @param value
         * @return
         */
        public static String convert(Object value) {
            if (value == null) {
                log.info("MicroTimeConverter: value is null");
                return null;
            }
            if (!(value instanceof Long)) {
                log.info("MicroTimeConverter: value is not Long, got {}", value.getClass());
                return null;
            }
            Instant i = Instant.EPOCH.plus((Long) value, ChronoUnit.MICROS);

            LocalTime time = i.atZone(ZoneOffset.UTC).toLocalTime();
            String formattedSecondsTimestamp= time.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));

            return formattedSecondsTimestamp;
        }
    }

    public static class MicroTimestampConverter {
        // DATETIME(4), DATETIME(5), DATETIME(6)
        // Represents the number of microseconds past the epoch and does not include time zone information.
        //ToDO: IF values exceed the ones supported by clickhouse
        public static String convert(Object value, ZoneId serverTimezone, ClickHouseDataType clickHouseDataType) {
            if (value == null) {
                log.info("MicroTimestampConverter: value is null");
                return null;
            }
            if (!(value instanceof Long)) {
                log.info("MicroTimestampConverter: value is not Long, got {}", value.getClass());
                return null;
            }
            Long epochMicroSeconds = (Long) value;

            //DateTime64 has a 8 digit precision.
            DateTimeFormatter destFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSS");
            if(clickHouseDataType == ClickHouseDataType.DateTime || clickHouseDataType == ClickHouseDataType.DateTime32) {
                destFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            }
            long epochSeconds = epochMicroSeconds / 1_000_000L;
            long nanoOffset = ( epochMicroSeconds % 1_000_000L ) * 1_000L ;
            Instant receivedDT = Instant.ofEpochSecond( epochSeconds, nanoOffset );

            Instant modifiedDT = checkIfDateTimeExceedsSupportedRange(receivedDT, clickHouseDataType);
            return modifiedDT.atZone(serverTimezone).format(destFormatter);
        }
    }

    public static class TimestampConverter {

        /**
         * Function to convert Debezium Timestamp fields to DATETIME(0), DATETIME(1), DATETIME(2), DATETIME(3)
         * Input represents number of milliseconds from Epoch and does not include timezone information.
         * Timestamp does not have microseconds
         * ISO formatted String.
         * @param value
         * @return
         */
        public static String convert(Object value, ClickHouseDataType clickHouseDataType, ZoneId sourceTimeZone, ZoneId serverTimezone) {
            if (value == null) {
                log.info("TimestampConverter: value is null");
                return null;
            }
            if (!(value instanceof Long)) {
                log.info("TimestampConverter: value is not Long, got {}", value.getClass());
                return null;
            }
            DateTimeFormatter destFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

            if(clickHouseDataType == ClickHouseDataType.DateTime || clickHouseDataType == ClickHouseDataType.DateTime32) {
                destFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            }

            Long epochMillis = (Long) value;
            try {
                ZonedDateTime zdt = Instant.ofEpochMilli(epochMillis).atZone(sourceTimeZone);
                Instant i = zdt.toInstant();
                Instant modifiedDTWithLimits = checkIfDateTimeExceedsSupportedRange(i, clickHouseDataType);
                return modifiedDTWithLimits.atZone(serverTimezone).format(destFormatter);
            } catch (Exception e) {
                log.info("TimestampConverter: Error converting value {}: {}", value, e.getMessage());
                return null;
            }
        }
    }

    public static Instant checkIfDateTimeExceedsSupportedRange(Instant providedDateTime, ClickHouseDataType clickHouseDataType) {

        if (providedDateTime == null || clickHouseDataType == null) {
            log.info("checkIfDateTimeExceedsSupportedRange: null input(s)");
            return providedDateTime;
        }

        if(clickHouseDataType == ClickHouseDataType.DateTime ||
                clickHouseDataType == ClickHouseDataType.DateTime32) {
            if(providedDateTime.isBefore(Instant.from(ofEpochMilli(DataTypeRange.DATETIME32_MIN)))) {
                return Instant.ofEpochSecond(DataTypeRange.DATETIME32_MIN);
            } else if(providedDateTime.isAfter(Instant.ofEpochSecond(DataTypeRange.DATETIME32_MAX))) {
                return Instant.ofEpochSecond(DataTypeRange.DATETIME32_MAX);
            }
        } else if(clickHouseDataType == ClickHouseDataType.DateTime64) {
            if (providedDateTime.isBefore(DataTypeRange.CLICKHOUSE_MIN_SUPPORTED_DATETIME64)) {
                return DataTypeRange.CLICKHOUSE_MIN_SUPPORTED_DATETIME64;
            } else if (providedDateTime.isAfter(DataTypeRange.CLICKHOUSE_MAX_SUPPORTED_DATETIME64)) {
                return DataTypeRange.CLICKHOUSE_MAX_SUPPORTED_DATETIME64;
            }
        }

        return providedDateTime;

    }
    public static class DateConverter {


        /**
         * MySQL: The DATE type is used for values with a date part but no time part.
         * MySQL retrieves and displays DATE values in 'YYYY-MM-DD' format. The supported range is '1000-01-01' to '9999-12-31'.
         *
         * Function to convert Debezium Date fields
         * to java.sql.Date
         * @param value - NUMBER OF DAYS since epoch.
         * @return
         */
        public static Date convert(Object value, ClickHouseDataType chDataType) {
            if (value == null) {
                log.info("DateConverter: value is null");
                return null;
            }
            if (!(value instanceof Integer)) {
                log.info("DateConverter: value is not Integer, got {}", value.getClass());
                return null;
            }
            Integer epochInDays = checkIfDateExceedsSupportedRange((Integer) value, chDataType);
            LocalDate d = LocalDate.ofEpochDay(epochInDays);

            return Date.valueOf(d);
        }

        /**
         * Function to check if the data exceeds the range.
         * Based on the Data types, the limits for Date and Date32 are checked and returned.
         * @param epochInDays
         * @param chDataType
         * @return
         */
        public static Integer checkIfDateExceedsSupportedRange(Integer epochInDays, ClickHouseDataType chDataType) {
            if (chDataType == null) {
                log.info("checkIfDateExceedsSupportedRange: chDataType is null");
                throw new IllegalArgumentException("chDataType cannot be null");
            }
            if(chDataType == ClickHouseDataType.Date32) {
                if (epochInDays < DataTypeRange.CLICKHOUSE_MIN_SUPPORTED_DATE32) {
                    return DataTypeRange.CLICKHOUSE_MIN_SUPPORTED_DATE32;
                } else if (epochInDays > DataTypeRange.CLICKHOUSE_MAX_SUPPORTED_DATE32) {
                    return DataTypeRange.CLICKHOUSE_MAX_SUPPORTED_DATE32;
                }
            } else if(chDataType == ClickHouseDataType.Date) {
                if(epochInDays < 0) {
                    return 0;
                } else if(epochInDays > U_INT16_MAX) {
                    return U_INT16_MAX;
                }
            } else {
                log.info("Unknown DATE field: {}", chDataType);
                throw new IllegalArgumentException("Unknown DATE field: " + chDataType);
            }

            return epochInDays;

        }
    }

    public static class ZonedTimestampConverter {

        /**
         * Function to convert timestamp(with timezone)
         * to formatted timestamp(DateTime clickhouse)
         * @param value
         * @return
         */
        public static String convert(Object value, ZoneId serverTimezone) {
            if (value == null) {
                log.info("ZonedTimestampConverter: value is null");
                return "INVALID_TIMESTAMP";
            }
            if (!(value instanceof String)) {
                log.info("ZonedTimestampConverter: value is not String, got {}", value.getClass());
                return "INVALID_TIMESTAMP";
            }

            String result = "";
            DateTimeFormatter destFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
                    .withZone(serverTimezone);

            // The order of this array matters,
            // for example you might truncate microseconds
            // to milliseconds(3) if .SSS is above .SSSSSS
            String[] date_formats = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SXXX",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SZ",
                    "yyyy-MM-dd'T'HH:mm:ssZ",
                    "yyyy-MM-dd'T'HH:mm:ss"
            };

            boolean parsingSuccesful = false;
            for (String formatString : date_formats) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatString)
                            .withZone(serverTimezone);
                    ZonedDateTime zd = ZonedDateTime.parse((String) value,
                            formatter.withZone(serverTimezone));

                    long dateTimeInMs = zd.toInstant().toEpochMilli();
                    if (dateTimeInMs > DataTypeRange.DATETIME64_MAX * 1000) {
                        zd = ZonedDateTime.ofInstant(
                                Instant.ofEpochSecond(DataTypeRange.DATETIME64_MAX),
                                serverTimezone);
                    } else if (dateTimeInMs < DataTypeRange.DATETIME64_MIN * 1000) {
                        zd = ZonedDateTime.ofInstant(
                                Instant.ofEpochSecond(DataTypeRange.DATETIME64_MIN),
                                serverTimezone);
                    }
                    result = zd.format(destFormatter);
                    parsingSuccesful = true;
                    break;
                } catch (Exception e) {
                    // Continue to next format
                }
            }
            if (!parsingSuccesful) {
                log.info("Error parsing zonedtimestamp {}", value);
                return "INVALID_TIMESTAMP";
            }
            return result;
        }
    }

    /**
     * Removes trailing zeros and an optional trailing dot from the input string.
     *
     * @param data The string to be processed.
     * @return the string without trailing zeros and dot.
     */
    static public String removeTrailingZeros(String data) {
        if (data == null) return null;
        return StringUtils.stripEnd(StringUtils.stripEnd(data, "0"), ".");
    }

    /**
     * BigDecimalConverter provides a method to truncate a BigDecimal
     * value based on supported limits.
     */
    public static class BigDecimalConverter {

        /**
         * Truncates the provided BigDecimal value to the maximum or minimum
         * supported value if it exceeds the ClickHouse limits.
         *
         * @param value the BigDecimal value to be truncated.
         * @return the truncated BigDecimal value.
         */
        public static BigDecimal truncate(BigDecimal value) {
            if (value == null) {
                log.info("BigDecimalConverter: value is null");
                return null;
            }
            if (value.compareTo(DECIMAL128_MAX) > 0) {
                log.warn("Decimal value {} is greater than max value {}",
                        value, DECIMAL128_MAX);
                return DECIMAL128_MAX;
            } else if (value.compareTo(DECIMAL128_MIN) < 0) {
                log.warn("Decimal value {} is less than min value {}",
                        value, DECIMAL128_MIN);
                return DECIMAL128_MIN;
            } else {
                return value;
            }
        }
    }
}
