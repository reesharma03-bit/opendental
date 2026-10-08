package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Open Dental's rules for account adjustments (https://www.opendental.com/site/apiadjustments.html),
 * checked before a change is saved so one Open Dental would reject never waits in the outbox.
 *
 * <ul>
 *   <li><b>Create</b> needs PatNum, AdjType (an adjustment-type definition), AdjAmt and AdjDate.</li>
 *   <li><b>Update</b> may change AdjDate, AdjAmt, AdjType, ProvNum, AdjNote, ProcNum and ClinicNum.</li>
 *   <li>AdjDate (and ProcDate) are "yyyy-MM-dd"; AdjDate can't be in the future.</li>
 *   <li>AdjAmt must be positive for a "+" adjustment type and negative for a "-" one.</li>
 *   <li>There is no delete.</li>
 * </ul>
 */
public final class AdjustmentRules {

    static final Set<String> UPDATABLE = Set.of("AdjDate", "AdjAmt", "AdjType", "ProvNum", "AdjNote", "ProcNum", "ClinicNum");

    private AdjustmentRules() {
    }

    /**
     * @param signOf the sign ("+" or "-") of an adjustment type, from the synced definitions;
     *               null when the type isn't known here (Open Dental then has the last word)
     */
    public static void checkCreate(Map<String, Object> body, Function<Long, String> signOf) {
        List<String> problems = new ArrayList<>();
        Long patNum = number(body, "PatNum", problems);
        if (patNum == null && !has(body, "PatNum")) problems.add("PatNum is required.");
        Long adjType = number(body, "AdjType", problems);
        if (adjType == null && !has(body, "AdjType")) problems.add("AdjType (the adjustment type) is required.");
        BigDecimal amount = amount(body, problems);
        if (amount == null && !has(body, "AdjAmt")) problems.add("AdjAmt is required.");
        if (!has(body, "AdjDate")) problems.add("AdjDate is required.");
        checkCommon(body, problems);
        checkSign(adjType, amount, signOf, problems);
        fail(problems);
    }

    /** {@code stored} is the adjustment as we hold it, for checking the amount against its type. */
    public static void checkUpdate(Map<String, Object> changes, Map<String, Object> stored, Function<Long, String> signOf) {
        List<String> problems = new ArrayList<>();
        for (String field : changes.keySet()) {
            if (UPDATABLE.stream().noneMatch(f -> f.equalsIgnoreCase(field))) {
                problems.add(field + " can't be changed. Open Dental allows: " + String.join(", ", UPDATABLE.stream().sorted().toList()) + ".");
            }
        }
        Long adjType = number(changes, "AdjType", problems);
        BigDecimal amount = amount(changes, problems);
        checkCommon(changes, problems);
        if (stored != null && (adjType != null || amount != null)) {
            checkSign(adjType != null ? adjType : number(stored, "AdjType", new ArrayList<>()),
                    amount != null ? amount : amount(stored, new ArrayList<>()), signOf, problems);
        }
        fail(problems);
    }

    private static void checkCommon(Map<String, Object> body, List<String> problems) {
        LocalDate adjDate = date(body, "AdjDate", problems);
        // A day of slack: the practice may be ahead of this server's clock. Open Dental checks exactly.
        if (adjDate != null && adjDate.isAfter(LocalDate.now().plusDays(1))) problems.add("AdjDate can't be in the future.");
        date(body, "ProcDate", problems);
        for (String field : List.of("ProvNum", "ProcNum", "ClinicNum")) number(body, field, problems);
    }

    private static void checkSign(Long adjType, BigDecimal amount, Function<Long, String> signOf, List<String> problems) {
        if (amount != null && amount.signum() == 0) {
            problems.add("AdjAmt can't be zero.");
            return;
        }
        if (adjType == null || amount == null || signOf == null) return;
        String sign = signOf.apply(adjType);
        if ("+".equals(sign) && amount.signum() < 0) problems.add("This adjustment type adds to the balance: AdjAmt must be positive.");
        if ("-".equals(sign) && amount.signum() > 0) problems.add("This adjustment type reduces the balance: AdjAmt must be negative.");
    }

    private static Long number(Map<String, Object> body, String field, List<String> problems) {
        String text = text(body, field);
        if (text.isEmpty()) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            problems.add(field + " must be a number.");
            return null;
        }
    }

    private static BigDecimal amount(Map<String, Object> body, List<String> problems) {
        String text = text(body, "AdjAmt");
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            problems.add("AdjAmt must be an amount, e.g. -25.00.");
            return null;
        }
    }

    private static LocalDate date(Map<String, Object> body, String field, List<String> problems) {
        String text = text(body, field);
        if (text.isEmpty()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            problems.add(field + " must be a date like 2026-10-08.");
            return null;
        }
    }

    private static boolean has(Map<String, Object> body, String field) {
        return !text(body, field).isEmpty();
    }

    /** Field value as text, whatever its casing in the request. */
    private static String text(Map<String, Object> body, String field) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            // Open Dental also returns "adjType" (the type's name) beside "AdjType" (its number).
            if (entry.getKey().equals(field)) {
                return entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            }
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field) && !(field.equals("AdjType") && entry.getKey().equals("adjType"))) {
                return entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            }
        }
        return "";
    }

    private static void fail(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
    }
}
