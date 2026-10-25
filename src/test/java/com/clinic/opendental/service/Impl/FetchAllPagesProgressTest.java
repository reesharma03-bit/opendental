package com.clinic.opendental.service.Impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchAllPagesProgressTest {

    private static List<Integer> page(Map<String, String> params) {
        int offset = Integer.parseInt(params.getOrDefault("Offset", "0"));
        return offset == 0 ? Collections.nCopies(100, 1) : offset == 100 ? Collections.nCopies(100, 2) : List.of(3);
    }

    @Test
    void reportsRowsReadAfterEachPage() {
        ReconciliationSyncService.CoreProgress progress = new ReconciliationSyncService.CoreProgress(() -> false);
        List<Integer> seen = new ArrayList<>();
        List<Integer> rows = ReconciliationSyncService.withProgress(progress, () ->
                ReconciliationSyncService.fetchAllPages(Map.of(), params -> {
                    seen.add(progress.read);
                    return page(params);
                }));

        assertThat(rows).hasSize(201);
        assertThat(seen).containsExactly(0, 100, 200);
        assertThat(progress.read).isEqualTo(201);
    }

    @Test
    void stopsBetweenPagesWhenAsked() {
        AtomicBoolean stop = new AtomicBoolean();
        ReconciliationSyncService.CoreProgress progress = new ReconciliationSyncService.CoreProgress(stop::get);

        assertThatThrownBy(() -> ReconciliationSyncService.withProgress(progress, () ->
                ReconciliationSyncService.fetchAllPages(Map.of(), params -> {
                    stop.set(true); // paused while the first page was read
                    return page(params);
                }))).isInstanceOf(SyncStoppedException.class);
        assertThat(progress.read).isEqualTo(100);
    }

    @Test
    void withoutProgressNothingIsReportedOrStopped() {
        assertThat(ReconciliationSyncService.fetchAllPages(Map.of(), params -> List.of(1))).containsExactly(1);
        assertThat(ReconciliationSyncService.stopRequested()).isFalse();
    }
}
