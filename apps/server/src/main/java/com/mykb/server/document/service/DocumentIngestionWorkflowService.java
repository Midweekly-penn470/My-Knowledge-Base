package com.mykb.server.document.service;

import com.mykb.server.common.exception.AppException;
import com.mykb.server.common.storage.ObjectStorageService;
import com.mykb.server.common.storage.StorageOperationException;
import com.mykb.server.dify.client.DifyClient;
import com.mykb.server.dify.client.DifyDatasetResult;
import com.mykb.server.dify.client.DifyDocumentUploadResult;
import com.mykb.server.dify.client.DifyIndexingStatusResult;
import com.mykb.server.dify.client.DifyOperationException;
import com.mykb.server.dify.config.DifyProperties;
import com.mykb.server.document.entity.DocumentIngestionTask;
import com.mykb.server.document.entity.KnowledgeDocument;
import com.mykb.server.document.repository.DocumentIngestionTaskRepository;
import com.mykb.server.knowledgebase.entity.KnowledgeBase;
import com.mykb.server.knowledgebase.repository.KnowledgeBaseRepository;
import com.mykb.server.ocr.client.OcrClient;
import com.mykb.server.ocr.client.OcrExtractResult;
import com.mykb.server.ocr.client.OcrOperationException;
import com.mykb.server.ocr.config.OcrProperties;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentIngestionWorkflowService {

  private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWorkflowService.class);

  private final DocumentIngestionTaskRepository ingestionTaskRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final ObjectStorageService objectStorageService;
  private final DifyClient difyClient;
  private final DifyProperties difyProperties;
  private final OcrClient ocrClient;
  private final OcrProperties ocrProperties;
  private final TransactionTemplate transactionTemplate;

  public DocumentIngestionWorkflowService(
      DocumentIngestionTaskRepository ingestionTaskRepository,
      KnowledgeBaseRepository knowledgeBaseRepository,
      ObjectStorageService objectStorageService,
      DifyClient difyClient,
      DifyProperties difyProperties,
      OcrClient ocrClient,
      OcrProperties ocrProperties,
      PlatformTransactionManager transactionManager) {
    this.ingestionTaskRepository = ingestionTaskRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.objectStorageService = objectStorageService;
    this.difyClient = difyClient;
    this.difyProperties = difyProperties;
    this.ocrClient = ocrClient;
    this.ocrProperties = ocrProperties;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Async("documentTaskExecutor")
  public void ingestAsync(UUID taskId) {
    DocumentIngestionTask.TaskStage failedStage = DocumentIngestionTask.TaskStage.DIFY_UPLOAD;
    try {
      IngestionContext context = loadContext(taskId);
      boolean ocrRequired = requiresOcr(context);
      failedStage =
          ocrRequired ? DocumentIngestionTask.TaskStage.OCR : DocumentIngestionTask.TaskStage.DIFY_UPLOAD;
      markRunning(taskId, failedStage);

      String datasetId = ensureDataset(context.knowledgeBaseId());
      byte[] fileBytes =
          objectStorageService.read(context.storageBucket(), context.storageObjectKey());

      DifyDocumentUploadResult uploadResult;
      String ocrEngine = null;
      if (ocrRequired) {
        OcrExtractResult extractResult =
            ocrClient.extractText(context.originalFilename(), context.contentType(), fileBytes);
        ocrEngine = trimToNull(extractResult.engine());
        failedStage = DocumentIngestionTask.TaskStage.DIFY_UPLOAD;
        updateStage(taskId, DocumentIngestionTask.TaskStage.DIFY_UPLOAD);
        uploadResult =
            difyClient.createTextDocument(
                datasetId, context.originalFilename(), requireExtractedText(extractResult.text()));
      } else {
        failedStage = DocumentIngestionTask.TaskStage.DIFY_UPLOAD;
        uploadResult =
            difyClient.uploadDocument(
                datasetId, context.originalFilename(), context.contentType(), fileBytes);
      }

      saveExternalTracking(taskId, uploadResult.documentId(), uploadResult.batchId(), ocrEngine);
      failedStage = DocumentIngestionTask.TaskStage.INDEXING;
      updateStage(taskId, DocumentIngestionTask.TaskStage.INDEXING);
      waitForIndexing(taskId, datasetId, uploadResult.batchId(), failedStage);
    } catch (OcrOperationException exception) {
      log.error("OCR ingestion failed. taskId={}", taskId, exception);
      markFailure(taskId, failedStage, "DOCUMENT_OCR_FAILED", exception.getMessage());
    } catch (DifyOperationException exception) {
      log.error(
          "Dify ingestion failed. taskId={}, code={}", taskId, exception.getCode(), exception);
      markFailure(taskId, failedStage, exception.getCode(), exception.getMessage());
    } catch (StorageOperationException exception) {
      log.error("Stored document read failed. taskId={}", taskId, exception);
      markFailure(
          taskId,
          failedStage,
          "DOCUMENT_STORAGE_READ_FAILED",
          "Failed to read the stored document");
    } catch (Exception exception) {
      log.error("Unexpected ingestion workflow failure. taskId={}", taskId, exception);
      markFailure(
          taskId,
          failedStage,
          "DOCUMENT_INGESTION_FAILED",
          "Document ingestion workflow failed");
    }
  }
  private void waitForIndexing(
      UUID taskId, String datasetId, String batchId, DocumentIngestionTask.TaskStage failedStage) {
    for (int attempt = 0; attempt < difyProperties.getMaxPollAttempts(); attempt++) {
      DifyIndexingStatusResult statusResult = difyClient.getIndexingStatus(datasetId, batchId);
      String normalizedStatus = normalizeStatus(statusResult.indexingStatus());
      if ("completed".equals(normalizedStatus)) {
        markSuccess(taskId);
        return;
      }
      if (isFailureStatus(normalizedStatus)) {
        markFailure(
            taskId,
            failedStage,
            "DIFY_INDEXING_FAILED",
            statusResult.errorMessage() == null || statusResult.errorMessage().isBlank()
                ? "Dify indexing failed"
                : trimMessage(statusResult.errorMessage()));
        return;
      }
      sleepBeforeNextPoll();
    }

    markFailure(taskId, failedStage, "DIFY_INDEXING_TIMEOUT", "Dify indexing timed out");
  }
  private void sleepBeforeNextPoll() {
    try {
      Thread.sleep(difyProperties.getPollInterval().toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DifyOperationException(
          "DIFY_INDEXING_INTERRUPTED", "Dify indexing polling was interrupted", exception);
    }
  }

  private boolean isFailureStatus(String normalizedStatus) {
    return "error".equals(normalizedStatus)
        || "failed".equals(normalizedStatus)
        || "paused".equals(normalizedStatus)
        || "stopped".equals(normalizedStatus);
  }

  private String ensureDataset(UUID knowledgeBaseId) {
    return transactionTemplate.execute(
        status -> {
          KnowledgeBase knowledgeBase =
              knowledgeBaseRepository
                  .findById(knowledgeBaseId)
                  .orElseThrow(
                      () ->
                          new AppException(
                              HttpStatus.NOT_FOUND,
                              "KNOWLEDGE_BASE_NOT_FOUND",
                              "Knowledge base does not exist"));
          if (knowledgeBase.getDifyDatasetId() != null
              && !knowledgeBase.getDifyDatasetId().isBlank()) {
            return knowledgeBase.getDifyDatasetId();
          }
          DifyDatasetResult datasetResult =
              difyClient.createDataset(knowledgeBase.getName(), knowledgeBase.getDescription());
          knowledgeBase.setDifyDatasetId(datasetResult.datasetId());
          log.info(
              "Dify dataset created. kbId={}, datasetId={}",
              knowledgeBase.getId(),
              datasetResult.datasetId());
          return datasetResult.datasetId();
        });
  }

  private IngestionContext loadContext(UUID taskId) {
    return transactionTemplate.execute(
        status -> {
          DocumentIngestionTask task =
              ingestionTaskRepository
                  .findDetailedById(taskId)
                  .orElseThrow(
                      () ->
                          new DifyOperationException(
                              "TASK_NOT_FOUND", "Document ingestion task does not exist"));
          KnowledgeDocument document = task.getDocument();
          return new IngestionContext(
              taskId,
              document.getId(),
              document.getKnowledgeBase().getId(),
              document.getOriginalFilename(),
              trimToNull(document.getContentType()),
              document.getStorageBucket(),
              document.getStorageObjectKey());
        });
  }

  private boolean requiresOcr(IngestionContext context) {
    if (!ocrProperties.isEnabled()) {
      return false;
    }
    if (context.contentType() != null
        && context.contentType().equalsIgnoreCase(ocrProperties.getPdfContentType())) {
      return true;
    }
    return context.originalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf");
  }

  private String requireExtractedText(String text) {
    String normalized = trimToNull(text);
    if (normalized == null) {
      throw new OcrOperationException("OCR service returned empty text");
    }
    return normalized;
  }

  private void markRunning(UUID taskId, DocumentIngestionTask.TaskStage stage) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DocumentIngestionTask task = getTaskOrThrow(taskId);
          task.setStatus(DocumentIngestionTask.TaskStatus.RUNNING);
          task.setCurrentStage(stage);
          task.setFailedStage(null);
          task.setStartedAt(Instant.now());
          task.setFinishedAt(null);
          task.setFailureCode(null);
          task.setFailureMessage(null);
          task.getDocument().setProcessingStatus(KnowledgeDocument.ProcessingStatus.PROCESSING);
        });
  }

  private void updateStage(UUID taskId, DocumentIngestionTask.TaskStage stage) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DocumentIngestionTask task = getTaskOrThrow(taskId);
          task.setCurrentStage(stage);
        });
  }

  private void saveExternalTracking(
      UUID taskId, String documentId, String batchId, String ocrEngine) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DocumentIngestionTask task = getTaskOrThrow(taskId);
          task.setExternalBatchId(batchId);
          task.setOcrEngine(trimToNull(ocrEngine));
          task.getDocument().setDifyDocumentId(documentId);
        });
  }

  private void markSuccess(UUID taskId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DocumentIngestionTask task = getTaskOrThrow(taskId);
          task.setStatus(DocumentIngestionTask.TaskStatus.SUCCEEDED);
          task.setCurrentStage(DocumentIngestionTask.TaskStage.COMPLETED);
          task.setFailedStage(null);
          task.setFinishedAt(Instant.now());
          task.setFailureCode(null);
          task.setFailureMessage(null);
          task.getDocument().setProcessingStatus(KnowledgeDocument.ProcessingStatus.SUCCEEDED);
          log.info(
              "Document ingestion completed. taskId={}, documentId={}",
              taskId,
              task.getDocument().getId());
        });
  }

  private void markFailure(
      UUID taskId,
      DocumentIngestionTask.TaskStage failedStage,
      String failureCode,
      String failureMessage) {
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            DocumentIngestionTask task = getTaskOrThrow(taskId);
            task.setStatus(DocumentIngestionTask.TaskStatus.FAILED);
            task.setCurrentStage(DocumentIngestionTask.TaskStage.FAILED);
            task.setFailedStage(failedStage);
            task.setFinishedAt(Instant.now());
            task.setFailureCode(failureCode);
            task.setFailureMessage(trimMessage(failureMessage));
            task.getDocument().setProcessingStatus(KnowledgeDocument.ProcessingStatus.FAILED);
          });
    } catch (DataAccessException exception) {
      log.error("Failed to persist ingestion failure state. taskId={}", taskId, exception);
    }
  }
  private DocumentIngestionTask getTaskOrThrow(UUID taskId) {
    return ingestionTaskRepository
        .findDetailedById(taskId)
        .orElseThrow(
            () ->
                new DifyOperationException(
                    "TASK_NOT_FOUND", "Document ingestion task does not exist"));
  }

  private String normalizeStatus(String status) {
    return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
  }

  private String trimMessage(String message) {
    if (message == null) {
      return null;
    }
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private record IngestionContext(
      UUID taskId,
      UUID documentId,
      UUID knowledgeBaseId,
      String originalFilename,
      String contentType,
      String storageBucket,
      String storageObjectKey) {}
}
