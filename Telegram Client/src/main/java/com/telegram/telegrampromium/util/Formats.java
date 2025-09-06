package com.telegram.telegrampromium.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Time/Date formatting helpers for list views.
 */
public final class Formats {
    private static final DateTimeFormatter HM   = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD   = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter YMD  = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private Formats() {}

    /** Returns HH:mm if same day; MMM d if same year; else yyyy/MM/dd. */
    public static String friendlyTs(long epochMillis, ZoneId zone) {
        if (epochMillis <= 0) return "";
        ZonedDateTime zdt = Instant.ofEpochMilli(epochMillis).atZone(zone);
        LocalDate today = LocalDate.now(zone);
        if (zdt.toLocalDate().isEqual(today)) return HM.format(zdt);
        if (zdt.getYear() == today.getYear()) return MD.format(zdt);
        return YMD.format(zdt);
    }
}
