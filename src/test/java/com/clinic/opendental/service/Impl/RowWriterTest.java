package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RowWriterTest {

    private final Clinic clinic = clinic();
    private final ReconciliationSyncService.Stats stats = new ReconciliationSyncService.Stats();
    @SuppressWarnings("unchecked")
    private final CrudRepository<Long, Long> repository = mock(CrudRepository.class);
    private final List<String> logged = new ArrayList<>();

    private static Clinic clinic() {
        Clinic c = new Clinic();
        c.setClinicCode("C1");
        return c;
    }

    private RowWriter<Long> writer(RowWriter.Batch<Long> batch) {
        return new RowWriter<>("row", clinic, stats, repository, batch, id -> id, (id, action) -> logged.add(id + " " + action));
    }

    @Test
    void writesFullBatchesInOneGoAndTheRestOnClose() {
        List<Integer> batches = new ArrayList<>();
        RowWriter<Long> writer = writer((inserts, updates) -> batches.add(inserts.size() + updates.size()));
        for (long i = 0; i < RowWriter.BATCH_SIZE + 3; i++) writer.insert(i);
        writer.update(9_999L);
        writer.close();

        assertThat(batches).containsExactly(RowWriter.BATCH_SIZE, 4);
        assertThat(stats.inserted).isEqualTo(RowWriter.BATCH_SIZE + 3);
        assertThat(stats.updated).isEqualTo(1);
        assertThat(logged).contains("9999 UPDATE (changed)", "0 INSERT (missing)");
        verify(repository, never()).save(any());
    }

    @Test
    void aFailedBatchIsSplitUntilOnlyTheBadRowFails() {
        List<List<Long>> written = new ArrayList<>();
        RowWriter<Long> writer = writer((inserts, updates) -> {
            if (inserts.contains(2L)) throw new RuntimeException("violates foreign key constraint");
            written.add(List.copyOf(inserts));
        });
        for (long i = 0; i < 8; i++) writer.insert(i);
        writer.close();

        assertThat(stats.inserted).isEqualTo(7);
        assertThat(stats.failed).isEqualTo(1);
        assertThat(written.stream().flatMap(List::stream)).containsExactlyInAnyOrder(0L, 1L, 3L, 4L, 5L, 6L, 7L);
        assertThat(written).hasSize(3); // [4..7], [0, 1], [3]
        verify(repository, never()).save(any());
    }

    @Test
    void withoutBatchSupportRowsAreSavedOneByOne() {
        RowWriter<Long> writer = writer(null);
        writer.insert(1L);
        writer.close();

        verify(repository).save(1L);
        assertThat(stats.inserted).isEqualTo(1);
    }

    @Test
    void stopsWritingOnceTheSyncIsStopped() {
        AtomicBoolean stop = new AtomicBoolean();
        List<Integer> batches = new ArrayList<>();
        ReconciliationSyncService.CoreProgress progress = new ReconciliationSyncService.CoreProgress(stop::get);

        assertThatThrownBy(() -> ReconciliationSyncService.withProgress(progress, () -> {
            RowWriter<Long> writer = writer((inserts, updates) -> batches.add(inserts.size()));
            for (long i = 0; i < RowWriter.BATCH_SIZE; i++) writer.insert(i); // first batch written
            stop.set(true);
            for (long i = 0; i < RowWriter.BATCH_SIZE * 2L; i++) writer.insert(i);
            writer.close();
            return null;
        })).isInstanceOf(SyncStoppedException.class);

        assertThat(batches).containsExactly(RowWriter.BATCH_SIZE);
        assertThat(progress.written).isEqualTo(RowWriter.BATCH_SIZE);
    }
}
