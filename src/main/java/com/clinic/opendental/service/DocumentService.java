package com.clinic.opendental.service;

import com.clinic.opendental.dto.document.*;

import java.util.List;
import java.util.Map;

public interface DocumentService {

    // GET /documents/{docNum}
    DocumentResponse getDocument(Long docNum);

    // GET /documents
    List<DocumentResponse> getDocuments(Map<String, String> params);

    // POST /documents/Upload
    DocumentResponse uploadDocument(UploadDocumentRequest request);

    // POST /documents/SetByUrl
    DocumentResponse setByUrl(SetByUrlRequest request);

    // POST /documents/UploadSftp
    DocumentResponse uploadSftp(UploadSftpRequest request);

    // POST /documents/DownloadSftp
    String downloadSftp(DownloadSftpRequest request);

    // POST /documents/Thumbnails
    List<ThumbnailResult> getThumbnails(ThumbnailsRequest request);

    // POST /documents/DownloadMount
    List<ThumbnailResult> downloadMount(DownloadMountRequest request);

    // PUT /documents/{docNum}
    DocumentResponse updateDocument(Long docNum, UpdateDocumentRequest request);

    // DELETE /documents/{docNum}
    void deleteDocument(Long docNum);
}