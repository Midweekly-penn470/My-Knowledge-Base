package com.mykb.server.document.controller;

import com.mykb.server.common.api.ApiResponse;
import com.mykb.server.common.security.AuthenticatedUser;
import com.mykb.server.document.dto.DocumentUploadResponse;
import com.mykb.server.document.dto.IngestionTaskResponse;
import com.mykb.server.document.dto.KnowledgeDocumentResponse;
import com.mykb.server.document.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}")
public class DocumentController {

  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<DocumentUploadResponse>> upload(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID knowledgeBaseId,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(documentService.upload(user, knowledgeBaseId, file)));
  }

  @DeleteMapping("/documents/{documentId}")
  public ResponseEntity<Void> deleteDocument(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID knowledgeBaseId,
      @PathVariable UUID documentId) {
    documentService.deleteFailedDocument(user, knowledgeBaseId, documentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/documents")
  public ApiResponse<List<KnowledgeDocumentResponse>> listDocuments(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID knowledgeBaseId) {
    return ApiResponse.success(documentService.listDocuments(user, knowledgeBaseId));
  }

  @PostMapping("/ingestion-tasks/{taskId}/retry")
  public ResponseEntity<ApiResponse<IngestionTaskResponse>> retryTask(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID knowledgeBaseId,
      @PathVariable UUID taskId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(documentService.retryFailedTask(user, knowledgeBaseId, taskId)));
  }

  @GetMapping("/ingestion-tasks")
  public ApiResponse<List<IngestionTaskResponse>> listTasks(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID knowledgeBaseId) {
    return ApiResponse.success(documentService.listTasks(user, knowledgeBaseId));
  }
}
