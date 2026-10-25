package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.repository.CrudRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Saves the rows a reconciliation found new or changed, {@value #BATCH_SIZE} per transaction
 * (one row per round trip to Supabase took hours for a large practice). When a batch fails it is
 * split in halves until the bad rows are found, so only they fail. Once the sync is asked to stop,
 * nothing more is written and {@link #close()} throws {@link SyncStoppedException}.
 */
@Slf4j
final class RowWriter<E> {

    static final int BATCH_SIZE = 500;

    /** Writes one batch in a single transaction. */
    interface Batch<E> {
        void write(List<E> inserts, List<E> updates);
    }

    private final String entity;
    private final Clinic clinic;
    private final ReconciliationSyncService.Stats stats;
    private final CrudRepository<E, ?> repository;
    /** Null: no transaction support (unit tests); rows are saved one by one. */
    private final Batch<E> batch;
    private final Function<E, Long> idOf;
    private final BiConsumer<Long, String> logged;
    private final List<E> inserts = new ArrayList<>();
    private final List<E> updates = new ArrayList<>();
    private boolean stopped;

    RowWriter(String entity, Clinic clinic, ReconciliationSyncService.Stats stats, CrudRepository<E, ?> repository,
              Batch<E> batch, Function<E, Long> idOf, BiConsumer<Long, String> logged) {
        this.entity = entity;
        this.clinic = clinic;
        this.stats = stats;
        this.repository = repository;
        this.batch = batch;
        this.idOf = idOf;
        this.logged = logged;
    }

    void insert(E row) {
        if (stopped) return;
        inserts.add(row);
        if (inserts.size() + updates.size() >= BATCH_SIZE) flush();
    }

    void update(E row) {
        if (stopped) return;
        updates.add(row);
        if (inserts.size() + updates.size() >= BATCH_SIZE) flush();
    }

    /** Writes what is waiting; throws {@link SyncStoppedException} when the sync was stopped. */
    void commit() {
        flush();
        if (stopped || ReconciliationSyncService.stopRequested()) throw new SyncStoppedException();
    }

    void close() {
        commit();
    }

    private void flush() {
        if (inserts.isEmpty() && updates.isEmpty()) return;
        if (ReconciliationSyncService.stopRequested()) {
            stopped = true;
            clear();
            return;
        }
        if (batch == null) {
            one(inserts, true);
            one(updates, false);
        } else if (!tryBatch(inserts, updates)) {
            split(new ArrayList<>(inserts), true);
            split(new ArrayList<>(updates), false);
        }
        clear();
    }

    private boolean tryBatch(List<E> ins, List<E> upd) {
        try {
            batch.write(ins, upd);
            ins.forEach(row -> done(row, true));
            upd.forEach(row -> done(row, false));
            return true;
        } catch (Exception e) {
            if (ins.size() + upd.size() == 1) {
                E row = ins.isEmpty() ? upd.get(0) : ins.get(0);
                stats.failed++;
                log.error("Failed to reconcile {} {} from clinic {}: {}", entity, idOf.apply(row), clinic.getClinicCode(),
                        NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            }
            return false;
        }
    }

    /** Writes {@code rows} in halves, down to single rows, so one bad row doesn't fail the rest. */
    private void split(List<E> rows, boolean insert) {
        if (rows.isEmpty()) return;
        boolean ok = insert ? tryBatch(rows, List.of()) : tryBatch(List.of(), rows);
        if (ok || rows.size() == 1) return;
        int half = rows.size() / 2;
        split(rows.subList(0, half), insert);
        split(rows.subList(half, rows.size()), insert);
    }

    private void one(List<E> rows, boolean insert) {
        for (E row : rows) {
            try {
                repository.save(row);
                done(row, insert);
            } catch (Exception e) {
                stats.failed++;
                log.error("Failed to reconcile {} {} from clinic {}: {}", entity, idOf.apply(row), clinic.getClinicCode(), e.getMessage());
            }
        }
    }

    private void done(E row, boolean insert) {
        if (insert) stats.inserted++;
        else stats.updated++;
        logged.accept(idOf.apply(row), insert ? "INSERT (missing)" : "UPDATE (changed)");
        ReconciliationSyncService.CoreProgress progress = ReconciliationSyncService.progress();
        if (progress != null) progress.written++;
    }

    private void clear() {
        inserts.clear();
        updates.clear();
    }
}
