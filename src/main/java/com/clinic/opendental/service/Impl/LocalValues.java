package com.clinic.opendental.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Parses the text values of Open Dental requests for the copy we keep locally
 * before the request reaches Open Dental. Values that do not parse are left
 * empty locally; Open Dental's own copy replaces them once it is pushed.
 */
final class LocalValues {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_NO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LocalValues() {
    }

    static LocalDate date(String value) {
        if (isEmptyDate(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static LocalDateTime dateTime(String value) {
        if (isEmptyDate(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        for (DateTimeFormatter format : new DateTimeFormatter[]{DATE_TIME, DATE_TIME_NO_SECONDS}) {
            try {
                return LocalDateTime.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        LocalDate date = date(text);
        return date == null ? null : date.atStartOfDay();
    }

    static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isEmptyDate(String value) {
        return value == null || value.isBlank() || value.startsWith("0001-01-01");
    }
}
