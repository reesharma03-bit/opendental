package com.clinic.opendental.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.dto.document.DocumentResponse;
import com.clinic.opendental.dto.document.DownloadMountRequest;
import com.clinic.opendental.dto.document.DownloadSftpRequest;
import com.clinic.opendental.dto.document.SetByUrlRequest;
import com.clinic.opendental.dto.document.ThumbnailResult;
import com.clinic.opendental.dto.document.ThumbnailsRequest;
import com.clinic.opendental.dto.document.UpdateDocumentRequest;
import com.clinic.opendental.dto.document.UploadDocumentRequest;
import com.clinic.opendental.dto.document.UploadSftpRequest;
import com.clinic.opendental.service.DocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentsController {

    private final DocumentService documentService;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * GET /documents/{docNum} - Get a single document
     */
    @GetMapping("/{docNum}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long docNum) {

        return ResponseEntity.ok(
                documentService.getDocument(docNum));
    }

    /**
     * GET /documents - Get multiple documents (PatNum required)
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                documentService.getDocuments(params));
    }

    // ========================================================================
    // POST Endpoints
    // ========================================================================

    /**
     * POST /documents/Upload - Upload a document via rawBase64
     */
    @PostMapping("/Upload")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @Valid @RequestBody UploadDocumentRequest request) {

        DocumentResponse response = documentService.uploadDocument(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /documents/SetByUrl - Set a document by URL
     */
    @PostMapping("/SetByUrl")
    public ResponseEntity<DocumentResponse> setByUrl(
            @Valid @RequestBody SetByUrlRequest request) {

        DocumentResponse response = documentService.setByUrl(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /documents/UploadSftp - Upload a document via SFTP
     */
    @PostMapping("/UploadSftp")
    public ResponseEntity<DocumentResponse> uploadSftp(
            @Valid @RequestBody UploadSftpRequest request) {

        DocumentResponse response = documentService.uploadSftp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /documents/DownloadSftp - Download a document via SFTP
     */
    @PostMapping("/DownloadSftp")
    public ResponseEntity<String> downloadSftp(
            @Valid @RequestBody DownloadSftpRequest request) {

        String filePath = documentService.downloadSftp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(filePath);
    }

    /**
     * POST /documents/Thumbnails - Get thumbnails for all images of a patient
     */
    @PostMapping("/Thumbnails")
    public ResponseEntity<List<ThumbnailResult>> getThumbnails(
            @Valid @RequestBody ThumbnailsRequest request) {

        List<ThumbnailResult> results = documentService.getThumbnails(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(results);
    }

    /**
     * POST /documents/DownloadMount - Download all images for a mount
     */
    @PostMapping("/DownloadMount")
    public ResponseEntity<List<ThumbnailResult>> downloadMount(
            @Valid @RequestBody DownloadMountRequest request) {

        List<ThumbnailResult> results = documentService.downloadMount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(results);
    }

    // ========================================================================
    // PUT Endpoints
    // ========================================================================

    /**
     * PUT /documents/{docNum} - Update a document's info
     */
    @PutMapping("/{docNum}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long docNum,
            @Valid @RequestBody UpdateDocumentRequest request) {

        return ResponseEntity.ok(
                documentService.updateDocument(docNum, request));
    }

    // ========================================================================
    // DELETE Endpoints
    // ========================================================================

    /**
     * DELETE /documents/{docNum} - Delete a document
     */
    @DeleteMapping("/{docNum}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long docNum) {

        documentService.deleteDocument(docNum);
        return ResponseEntity.ok().build();
    }
}