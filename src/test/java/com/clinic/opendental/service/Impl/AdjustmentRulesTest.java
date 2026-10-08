package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Open Dental's adjustment rules (apiadjustments.html), enforced before anything is saved. */
class AdjustmentRulesTest {

    /** Definition 1 is a negative ("Misc Neg Adjustment") type, 2 a positive one. */
    private static final Function<Long, String> SIGNS = defNum -> defNum == 1 ? "-" : defNum == 2 ? "+" : null;
    private static final String TODAY = LocalDate.now().toString();

    @Test
    void aDiscountCanBeCreated() {
        assertThatCode(() -> AdjustmentRules.checkCreate(Map.of(
                "PatNum", 15, "AdjType", 1, "AdjAmt", -24.99, "AdjDate", TODAY,
                "ProvNum", 1, "AdjNote", "Discount", "ProcNum", 18, "ProcDate", "2022-06-10", "ClinicNum", 1), SIGNS))
                .doesNotThrowAnyException();
    }

    @Test
    void createNeedsPatientTypeAmountAndDate() {
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of(), SIGNS))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PatNum is required").hasMessageContaining("AdjType")
                .hasMessageContaining("AdjAmt is required").hasMessageContaining("AdjDate is required");
    }

    @Test
    void theAmountFollowsTheTypesSign() {
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 1, "AdjAmt", 25, "AdjDate", TODAY), SIGNS))
                .hasMessageContaining("must be negative");
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 2, "AdjAmt", -25, "AdjDate", TODAY), SIGNS))
                .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 2, "AdjAmt", 0, "AdjDate", TODAY), SIGNS))
                .hasMessageContaining("can't be zero");
        // A type we haven't synced yet: Open Dental decides.
        assertThatCode(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 9, "AdjAmt", 25, "AdjDate", TODAY), SIGNS))
                .doesNotThrowAnyException();
    }

    @Test
    void datesAreCheckedAndNotInTheFuture() {
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 2, "AdjAmt", 5,
                "AdjDate", LocalDate.now().plusDays(10).toString()), SIGNS)).hasMessageContaining("can't be in the future");
        assertThatThrownBy(() -> AdjustmentRules.checkCreate(Map.of("PatNum", 15, "AdjType", 2, "AdjAmt", 5,
                "AdjDate", "07/19/2022"), SIGNS)).hasMessageContaining("AdjDate must be a date");
    }

    @Test
    void updatesOnlyTouchWhatOpenDentalAllowsAndKeepTheSignRight() {
        Map<String, Object> stored = Map.of("AdjNum", 17, "PatNum", 21, "AdjType", 1, "adjType", "Misc Neg Adjustment", "AdjAmt", -25.0);

        assertThatCode(() -> AdjustmentRules.checkUpdate(Map.of("AdjAmt", -24.49, "AdjNote", "Corrected"), stored, SIGNS))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AdjustmentRules.checkUpdate(Map.of("PatNum", 22), stored, SIGNS))
                .hasMessageContaining("PatNum can't be changed");
        assertThatThrownBy(() -> AdjustmentRules.checkUpdate(Map.of("AdjAmt", 30), stored, SIGNS))
                .hasMessageContaining("must be negative");
        // Switching to a positive type with a positive amount is fine.
        assertThatCode(() -> AdjustmentRules.checkUpdate(Map.of("AdjType", 2, "AdjAmt", 39.5), stored, SIGNS))
                .doesNotThrowAnyException();
    }
}
