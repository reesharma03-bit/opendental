package com.clinic.opendental.service.Impl;

/**
 * The core step of a full sync was stopped part-way (sync paused, clinic disconnected). What
 * was saved is kept; the tables it finished are skipped when the sync continues.
 */
public class SyncStoppedException extends RuntimeException {
    public SyncStoppedException() {
        super("Sync stopped");
    }
}
