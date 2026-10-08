package com.clinic.opendental.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The patient last opened in Open Dental on each workstation, from Open Dental's
 * PatientSelected UI event (https://www.opendental.com/site/apievents.html). Kept in
 * memory only: it is short-lived, holds patient names, and is worthless after a restart.
 */
@Component
public class SelectedPatients {

    /** A selection is shown for this long, then forgotten. */
    static final Duration SHOWN_FOR = Duration.ofHours(8);
    private static final int MAX_WORKSTATIONS = 200;

    public record Selection(String workstation, long patNum, String name, Instant at) {
    }

    private final Map<UUID, Map<String, Selection>> byClinic = new ConcurrentHashMap<>();

    public void record(UUID clinicId, String workstation, long patNum, String name) {
        if (clinicId == null || patNum <= 0) {
            return;
        }
        String station = workstation == null || workstation.isBlank() ? "Unknown workstation" : workstation.trim();
        Map<String, Selection> stations = byClinic.computeIfAbsent(clinicId, id -> new ConcurrentHashMap<>());
        if (stations.size() >= MAX_WORKSTATIONS && !stations.containsKey(station)) {
            stations.values().stream().min(Comparator.comparing(Selection::at))
                    .ifPresent(oldest -> stations.remove(oldest.workstation()));
        }
        stations.put(station, new Selection(station, patNum, name == null ? "" : name.trim(), Instant.now()));
    }

    /** Recent selections for the clinic, newest first. */
    public List<Selection> recent(UUID clinicId) {
        Map<String, Selection> stations = byClinic.get(clinicId);
        if (stations == null) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(SHOWN_FOR);
        stations.values().removeIf(s -> s.at().isBefore(cutoff));
        return stations.values().stream().sorted(Comparator.comparing(Selection::at).reversed()).toList();
    }
}
