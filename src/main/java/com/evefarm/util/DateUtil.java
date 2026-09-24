package com.evefarm.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class DateUtil {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_FORMAT_WITH_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter EVE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EVE_MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EVE_CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    public static String format(Instant instant) {
        return instant == null ? "" : DISPLAY_FORMAT.format(instant);
    }

    public static String formatWithSeconds(Instant instant) {
        return instant == null ? "" : DISPLAY_FORMAT_WITH_SECONDS.format(instant);
    }

    public static String formatEveTime(Instant instant) {
        return instant == null ? "" : EVE_TIME_FORMAT.format(instant);
    }

    public static String formatEveMinute(Instant instant) {
        return instant == null ? "" : EVE_MINUTE_FORMAT.format(instant);
    }

    public static String formatEveClock(Instant instant) {
        return instant == null ? "" : EVE_CLOCK_FORMAT.format(instant);
    }

    public static String formatIsoInstant(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return "";
        }
        try {
            return format(Instant.parse(isoInstant));
        } catch (Exception e) {
            return isoInstant;
        }
    }

    public static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Instant startOfDay(LocalDate localDate) {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    public static Instant endOfDay(LocalDate localDate) {
        return localDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private DateUtil() {
    }
}
