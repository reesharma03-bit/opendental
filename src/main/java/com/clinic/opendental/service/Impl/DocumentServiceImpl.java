package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.document.*;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.DocumentId;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final OpenDentalClient client;
    private final DocumentRepository documentRepository;
    private final ClinicRepository clinicRepository;
    private final OdSyncService odSync;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long docNum) {
        try {
            DocumentResponse apiResponse = client.getDocument(docNum);
            saveDocumentToDb(apiResponse);
            return apiResponse;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for document {}, falling back to database: {}", docNum, e.getMessage());
            UUID clinicId = resolveClinicId();
            Document document = documentRepository.findById(new DocumentId(clinicId, docNum))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Document not found with DocNum: " + docNum));
            return toDocumentResponse(document);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(Map<String, String> params) {
        try {
            List<DocumentResponse> apiResponses = client.getDocuments(params);
            syncDocumentsToDb(apiResponses);
            return apiResponses;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for documents, falling back to database: {}", e.getMessage());
            return documentRepository.findAll().stream()
                    .map(this::toDocumentResponse)
                    .collect(Collectors.toList());
        }
    }

    // Upload, SetByUrl, update and delete go to our database first and are then pushed
    // to Open Dental (right away when it is reachable, otherwise from the retry queue).
    // A new document has a temporary negative DocNum until Open Dental assigns one.

    @Override
    public DocumentResponse uploadDocument(UploadDocumentRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.DOCUMENT, OdSyncService.CREATE, request,
                docNum -> documentRepository.save(newDocument(clinicId, docNum, request.getPatNum(),
                        OdSyncService.convert(request, UpdateDocumentRequest.class))));
        return loadSavedDocument(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public DocumentResponse setByUrl(SetByUrlRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.DOCUMENT, OdSyncService.SET_BY_URL, request,
                docNum -> documentRepository.save(newDocument(clinicId, docNum, request.getPatNum(),
                        OdSyncService.convert(request, UpdateDocumentRequest.class))));
        return loadSavedDocument(clinicId, odSync.pushNow(taskId));
    }

    @Override
    @Transactional
    public DocumentResponse uploadSftp(UploadSftpRequest request) {
        try {
            DocumentResponse response = client.uploadSftp(request);
            saveDocumentToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to upload document via SFTP: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload document via SFTP: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String downloadSftp(DownloadSftpRequest request) {
        try {
            return client.downloadSftp(request);
        } catch (Exception e) {
            log.error("Failed to download document via SFTP: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to download document via SFTP: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public List<ThumbnailResult> getThumbnails(ThumbnailsRequest request) {
        try {
            return client.getThumbnails(request);
        } catch (Exception e) {
            log.error("Failed to get thumbnails: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get thumbnails: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public List<ThumbnailResult> downloadMount(DownloadMountRequest request) {
        try {
            return client.downloadMount(request);
        } catch (Exception e) {
            log.error("Failed to download mount: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to download mount: " + e.getMessage());
        }
    }

    @Override
    public DocumentResponse updateDocument(Long docNum, UpdateDocumentRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.DOCUMENT, docNum);
        long taskId = odSync.recordChange(clinicId, OdSyncService.DOCUMENT, OdSyncService.UPDATE, docNum, request,
                () -> {
                    Document document = documentRepository.findById(new DocumentId(clinicId, docNum))
                            .orElseThrow(() -> documentNotFound(docNum));
                    applyRequest(document, request);
                    documentRepository.save(document);
                });
        return loadSavedDocument(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public void deleteDocument(Long docNum) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.DOCUMENT, docNum);
        Long taskId = odSync.recordDelete(clinicId, OdSyncService.DOCUMENT, docNum,
                () -> documentRepository.deleteById(new DocumentId(clinicId, docNum)));
        if (taskId != null) {
            odSync.pushNow(taskId);
        }
    }

    private static Document newDocument(UUID clinicId, long docNum, Long patNum, UpdateDocumentRequest fields) {
        Document document = Document.builder()
                .id(new DocumentId(clinicId, docNum))
                .patNum(patNum)
                .imgType("Document")
                .build();
        applyRequest(document, fields);
        return document;
    }

    private static void applyRequest(Document d, UpdateDocumentRequest r) {
        if (r.getDescription() != null) d.setDescription(r.getDescription());
        if (r.getDateCreated() != null) d.setDateCreated(LocalValues.dateTime(r.getDateCreated()));
        if (r.getDocCategory() != null) d.setDocCategory(r.getDocCategory());
        if (r.getImgType() != null) d.setImgType(r.getImgType());
        if (r.getToothNumbers() != null) d.setToothNumbers(r.getToothNumbers());
        if (r.getProvNum() != null) d.setProvNum(r.getProvNum());
        if (r.getPrintHeading() != null) d.setPrintHeading(r.getPrintHeading());
    }

    private DocumentResponse loadSavedDocument(UUID clinicId, long docNum) {
        return documentRepository.findById(new DocumentId(clinicId, docNum))
                .map(this::toDocumentResponse)
                .orElseThrow(() -> documentNotFound(docNum));
    }

    private static ApiException documentNotFound(Long docNum) {
        return new ApiException(HttpStatus.NOT_FOUND, "Document not found with DocNum: " + docNum);
    }

    // ========== Database sync helpers ==========

    @Transactional
    protected void syncDocumentsToDb(List<DocumentResponse> apiResponses) {
        for (DocumentResponse dto : apiResponses) {
            saveDocumentToDb(dto);
        }
    }

    @Transactional
    protected void saveDocumentToDb(DocumentResponse dto) {
        try {
            if (odSync.hasQueuedChanges(resolveClinicId(), OdSyncService.DOCUMENT, dto.getDocNum())) {
                return; // our newer copy has not reached Open Dental yet
            }
            Document document = toDocumentEntity(dto);
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to sync document {} to database: {}", dto.getDocNum(), e.getMessage());
        }
    }

    private UUID resolveClinicId() {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No active clinic configured. Please register a clinic in the clinics table.");
        }
        return clinics.get(0).getId();
    }

    /** Open Dental returns PatNum as text on documents. */
    private static Long parsePatNum(String patNum) {
        if (patNum == null || patNum.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(patNum.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Document toDocumentEntity(DocumentResponse dto) {
        UUID clinicId = resolveClinicId();

        Document.DocumentBuilder builder = Document.builder()
                .id(new DocumentId(clinicId, dto.getDocNum()))
                .patNum(parsePatNum(dto.getPatNum()))
                .description(dto.getDescription())
                .note(dto.getNote())
                .imgType(dto.getImgType())
                .toothNumbers(dto.getToothNumbers())
                .provNum(dto.getProvNum())
                .printHeading(dto.getPrintHeading());

        if (dto.getDocCategory() != null && !dto.getDocCategory().isEmpty()) {
            try {
                builder.docCategory(Long.parseLong(dto.getDocCategory()));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse docCategory '{}' as Long", dto.getDocCategory());
            }
        }

        if (dto.getFileName() != null && !dto.getFileName().isEmpty()) {
            builder.fileName(dto.getFileName());
        }

        if (dto.getDateCreated() != null && !dto.getDateCreated().isEmpty()
                && !dto.getDateCreated().equals("0001-01-01 00:00:00")
                && !dto.getDateCreated().equals("0001-01-01")) {
            try {
                builder.dateCreated(LocalDateTime.parse(dto.getDateCreated(), DATETIME_FORMAT));
            } catch (Exception e) {
                try {
                    builder.dateCreated(LocalDate.parse(dto.getDateCreated(), DATE_FORMAT).atStartOfDay());
                } catch (Exception ex) {
                    // ignore parse errors
                }
            }
        }

        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty()
                && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            try {
                builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        return builder.build();
    }

    private DocumentResponse toDocumentResponse(Document entity) {
        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .DocNum(entity.getId().getDocNum())
                .PatNum(entity.getPatNum() != null ? String.valueOf(entity.getPatNum()) : null)
                .Description(entity.getDescription())
                .Note(entity.getNote())
                .DocCategory(entity.getDocCategory())
                .FileName(entity.getFileName())
                .ImgType(entity.getImgType())
                .ToothNumbers(entity.getToothNumbers())
                .ProvNum(entity.getProvNum())
                .PrintHeading(entity.getPrintHeading());

        if (entity.getDateCreated() != null) {
            builder.DateCreated(entity.getDateCreated().format(DATETIME_FORMAT));
        }

        if (entity.getDateTStamp() != null) {
            builder.DateTStamp(entity.getDateTStamp().format(DATETIME_FORMAT));
        }

        return builder.build();
    }
}