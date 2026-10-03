package com.clinic.opendental.repository;

import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.DocumentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, DocumentId> {

    List<Document> findByIdClinicIdAndIdDocNum(UUID clinicId, Long docNum);

    List<Document> findByIdClinicId(UUID clinicId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Document d SET d.patNum = :realId WHERE d.id.clinicId = :clinicId AND d.patNum = :tempId")
    int movePatient(@Param("clinicId") UUID clinicId, @Param("tempId") Long tempId, @Param("realId") Long realId);
}