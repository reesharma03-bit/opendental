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

    @Override
    @Transactional
    public DocumentResponse uploadDocument(UploadDocumentRequest request) {
        try {
            DocumentResponse response = client.uploadDocument(request);
            saveDocumentToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to upload document via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload document: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public DocumentResponse setByUrl(SetByUrlRequest request) {
        try {
            DocumentResponse response = client.setByUrl(request);
            saveDocumentToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to set document by URL via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to set document by URL: " + e.getMessage());
        }
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
    @Transactional
    public DocumentResponse updateDocument(Long docNum, UpdateDocumentRequest request) {
        try {
            DocumentResponse response = client.updateDocument(docNum, request);
            saveDocumentToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to update document via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update document: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteDocument(Long docNum) {
        try {
            client.deleteDocument(docNum);
            UUID clinicId = resolveClinicId();
            documentRepository.deleteById(new DocumentId(clinicId, docNum));
        } catch (Exception e) {
            log.error("Failed to delete document via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete document: " + e.getMessage());
        }
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

    private Document toDocumentEntity(DocumentResponse dto) {
        UUID clinicId = resolveClinicId();

        Document.DocumentBuilder builder = Document.builder()
                .id(new DocumentId(clinicId, dto.getDocNum()))
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