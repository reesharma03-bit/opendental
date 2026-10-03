package com.clinic.opendental.repository;

import com.clinic.opendental.model.OdSyncTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface OdSyncTaskRepository extends JpaRepository<OdSyncTask, Long> {

    /** Statuses that still owe Open Dental a change. */
    List<String> OPEN = List.of(OdSyncTask.PENDING, OdSyncTask.IN_PROGRESS, OdSyncTask.FAILED);

    @Query("SELECT t.id FROM OdSyncTask t WHERE t.status = 'PENDING' AND t.nextAttemptAt <= :now ORDER BY t.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable page);

    /** Atomically takes a task so only one worker (request thread, scheduler, other instance) pushes it. */
    @Modifying
    @Query("UPDATE OdSyncTask t SET t.status = 'IN_PROGRESS', t.updatedAt = :now WHERE t.id = :id AND t.status = 'PENDING'")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Releases tasks left IN_PROGRESS by a crash so they are retried. */
    @Modifying
    @Query("UPDATE OdSyncTask t SET t.status = 'PENDING' WHERE t.status = 'IN_PROGRESS' AND t.updatedAt < :before")
    int releaseStale(@Param("before") LocalDateTime before);

    List<OdSyncTask> findByClinicIdAndEntityTypeAndLocalIdOrderByIdAsc(UUID clinicId, String entityType, Long localId);

    @Query("SELECT COUNT(t) > 0 FROM OdSyncTask t WHERE t.clinicId = :clinicId AND t.entityType = :entityType "
            + "AND t.localId = :localId AND t.id < :id AND t.status IN :statuses")
    boolean existsEarlier(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                          @Param("localId") Long localId, @Param("id") Long id,
                          @Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(t) > 0 FROM OdSyncTask t WHERE t.clinicId = :clinicId AND t.entityType = :entityType "
            + "AND t.localId = :localId AND t.id > :id AND t.status IN :statuses")
    boolean existsLater(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                        @Param("localId") Long localId, @Param("id") Long id,
                        @Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(t) > 0 FROM OdSyncTask t WHERE t.clinicId = :clinicId AND t.entityType = :entityType "
            + "AND t.localId = :localId AND t.status IN :statuses")
    boolean existsForRecord(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                            @Param("localId") Long localId, @Param("statuses") Collection<String> statuses);

    @Query("SELECT DISTINCT t.localId FROM OdSyncTask t WHERE t.clinicId = :clinicId AND t.entityType = :entityType "
            + "AND t.status IN :statuses")
    Set<Long> findLocalIds(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                           @Param("statuses") Collection<String> statuses);

    /** After Open Dental assigns a real key, later queued changes for the same record follow it. */
    @Modifying
    @Query("UPDATE OdSyncTask t SET t.localId = :realId WHERE t.clinicId = :clinicId AND t.entityType = :entityType "
            + "AND t.localId = :tempId AND t.status IN :statuses")
    int moveToRealId(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                     @Param("tempId") Long tempId, @Param("realId") Long realId,
                     @Param("statuses") Collection<String> statuses);

    @Modifying
    @Query("UPDATE OdSyncTask t SET t.status = 'CANCELLED', t.payload = null WHERE t.clinicId = :clinicId "
            + "AND t.entityType = :entityType AND t.localId = :localId AND t.status IN :statuses")
    int cancelForRecord(@Param("clinicId") UUID clinicId, @Param("entityType") String entityType,
                        @Param("localId") Long localId, @Param("statuses") Collection<String> statuses);

    List<OdSyncTask> findByStatusInOrderByIdDesc(Collection<String> statuses, Pageable page);
}
