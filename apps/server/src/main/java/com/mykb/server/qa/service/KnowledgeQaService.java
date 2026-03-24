package com.mykb.server.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykb.server.common.exception.AppException;
import com.mykb.server.common.security.AuthenticatedUser;
import com.mykb.server.dify.client.DifyClient;
import com.mykb.server.dify.client.DifyCompletionEvent;
import com.mykb.server.dify.client.DifyCompletionRequest;
import com.mykb.server.dify.client.DifyOperationException;
import com.mykb.server.dify.client.DifyRetrievedChunk;
import com.mykb.server.dify.config.DifyProperties;
import com.mykb.server.knowledgebase.entity.KnowledgeBase;
import com.mykb.server.knowledgebase.repository.KnowledgeBaseRepository;
import com.mykb.server.knowledgebase.repository.KnowledgeBaseShareRepository;
import com.mykb.server.qa.dto.QaSourceResponse;
import com.mykb.server.qa.dto.QaStreamRequest;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class KnowledgeQaService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeQaService.class);

  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeBaseShareRepository knowledgeBaseShareRepository;
  private final DifyClient difyClient;
  private final DifyProperties difyProperties;
  private final TaskExecutor qaStreamExecutor;
  private final ObjectMapper objectMapper;

  public KnowledgeQaService(
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeBaseShareRepository knowledgeBaseShareRepository,
      DifyClient difyClient,
      DifyProperties difyProperties,
      @Qualifier("qaStreamExecutor") TaskExecutor qaStreamExecutor,
      ObjectMapper objectMapper) {
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeBaseShareRepository = knowledgeBaseShareRepository;
    this.difyClient = difyClient;
    this.difyProperties = difyProperties;
    this.qaStreamExecutor = qaStreamExecutor;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public SseEmitter streamAnswer(
      AuthenticatedUser currentUser, UUID knowledgeBaseId, QaStreamRequest request) {
    KnowledgeBase knowledgeBase = getAccessibleKnowledgeBase(currentUser.userId(), knowledgeBaseId);
    if (knowledgeBase.getDifyDatasetId() == null || knowledgeBase.getDifyDatasetId().isBlank()) {
      throw new AppException(
          HttpStatus.CONFLICT,
          "KNOWLEDGE_BASE_NOT_READY",
          "Knowledge base is not ready for QA because Dify dataset is missing");
    }

    String query = request.query().trim();
    SseEmitter emitter = new SseEmitter(difyProperties.getQaSseTimeout().toMillis());
    emitter.onTimeout(
        () -> {
          log.warn(
              "QA stream timed out. kbId={}, userId={}", knowledgeBaseId, currentUser.userId());
          emitter.complete();
        });
    emitter.onCompletion(
        () ->
            log.debug(
                "QA stream completed. kbId={}, userId={}", knowledgeBaseId, currentUser.userId()));

    qaStreamExecutor.execute(
        () -> executeStream(emitter, currentUser, knowledgeBase, query, knowledgeBaseId));
    return emitter;
  }

  private void executeStream(
      SseEmitter emitter,
      AuthenticatedUser currentUser,
      KnowledgeBase knowledgeBase,
      String query,
      UUID knowledgeBaseId) {
    try {
      List<DifyRetrievedChunk> retrievedChunks =
          filterChunks(difyClient.retrieveChunks(knowledgeBase.getDifyDatasetId(), query));
      List<QaSourceResponse> sources = toSourceResponses(retrievedChunks);

      sendEvent(
          emitter, "start", Map.of("knowledgeBaseId", knowledgeBaseId.toString(), "query", query));
      sendEvent(emitter, "sources", sources);

      if (sources.isEmpty()) {
        completeWithRefusal(emitter);
        return;
      }

      String context = buildContext(retrievedChunks);
      StringBuilder answerBuilder = new StringBuilder();
      AtomicReference<String> messageIdRef = new AtomicReference<>();
      AtomicReference<String> taskIdRef = new AtomicReference<>();

      difyClient.streamCompletion(
          new DifyCompletionRequest(
              query, context, knowledgeBase.getName(), currentUser.userId().toString()),
          event -> handleStreamEvent(emitter, event, answerBuilder, messageIdRef, taskIdRef));

      sendDoneEvent(
          emitter,
          answerBuilder.toString(),
          false,
          sources.size(),
          messageIdRef.get(),
          taskIdRef.get());
      emitter.complete();
    } catch (DifyOperationException exception) {
      log.error(
          "QA stream failed. kbId={}, userId={}, code={}",
          knowledgeBaseId,
          currentUser.userId(),
          exception.getCode(),
          exception);
      emitError(emitter, exception.getCode(), exception.getMessage());
    } catch (IOException exception) {
      log.warn(
          "QA emitter disconnected. kbId={}, userId={}",
          knowledgeBaseId,
          currentUser.userId(),
          exception);
      emitter.complete();
    } catch (Exception exception) {
      log.error(
          "Unexpected QA stream failure. kbId={}, userId={}",
          knowledgeBaseId,
          currentUser.userId(),
          exception);
      emitError(emitter, "QA_STREAM_FAILED", "QA streaming failed");
    }
  }

  private void handleStreamEvent(
      SseEmitter emitter,
      DifyCompletionEvent event,
      StringBuilder answerBuilder,
      AtomicReference<String> messageIdRef,
      AtomicReference<String> taskIdRef) {
    messageIdRef.compareAndSet(null, event.messageId());
    taskIdRef.compareAndSet(null, event.taskId());
    if (event.answer() == null || event.answer().isBlank()) {
      return;
    }
    answerBuilder.append(event.answer());
    try {
      sendEvent(emitter, "message", Map.of("delta", event.answer()));
    } catch (IOException exception) {
      throw new DifyOperationException(
          "QA_STREAM_DISCONNECTED", "QA client disconnected while streaming answer", exception);
    }
  }

  private List<DifyRetrievedChunk> filterChunks(List<DifyRetrievedChunk> retrievedChunks) {
    return retrievedChunks.stream()
        .filter(chunk -> chunk.content() != null && !chunk.content().isBlank())
        .filter(
            chunk ->
                chunk.score() == null || chunk.score() >= difyProperties.getRetrievalMinScore())
        .sorted(
            Comparator.comparing(
                DifyRetrievedChunk::score, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(difyProperties.getRetrievalTopK())
        .toList();
  }
  private List<QaSourceResponse> toSourceResponses(List<DifyRetrievedChunk> retrievedChunks) {
    return retrievedChunks.stream()
        .map(
            chunk ->
                new QaSourceResponse(
                    chunk.documentId(),
                    chunk.segmentId(),
                    chunk.documentName(),
                    chunk.content(),
                    chunk.score()))
        .toList();
  }

  private String buildContext(List<DifyRetrievedChunk> retrievedChunks) {
    int maxChars = difyProperties.getQaContextMaxChars();
    StringBuilder builder = new StringBuilder();
    int index = 1;
    for (DifyRetrievedChunk chunk : retrievedChunks) {
      String section =
          """
          [Source %d]
          Document: %s
          Content: %s

          """
              .formatted(
                  index++,
                  chunk.documentName() == null || chunk.documentName().isBlank()
                      ? "unknown"
                      : chunk.documentName(),
                  chunk.content().trim());
      if (builder.length() + section.length() <= maxChars) {
        builder.append(section);
        continue;
      }
      int remaining = maxChars - builder.length();
      if (remaining > 0) {
        builder.append(section, 0, Math.min(section.length(), remaining));
      }
      break;
    }
    return builder.toString().trim();
  }

  private void completeWithRefusal(SseEmitter emitter) throws IOException {
    String refusalMessage = difyProperties.getQaRefusalMessage();
    sendEvent(emitter, "message", Map.of("delta", refusalMessage));
    sendDoneEvent(emitter, refusalMessage, true, 0, null, null);
    emitter.complete();
  }

  private void sendDoneEvent(
      SseEmitter emitter,
      String answer,
      boolean refusal,
      int sourceCount,
      String messageId,
      String taskId)
      throws IOException {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("answer", answer);
    payload.put("refusal", refusal);
    payload.put("sourceCount", sourceCount);
    if (messageId != null && !messageId.isBlank()) {
      payload.put("messageId", messageId);
    }
    if (taskId != null && !taskId.isBlank()) {
      payload.put("taskId", taskId);
    }
    sendEvent(emitter, "done", payload);
  }

  private void emitError(SseEmitter emitter, String code, String message) {
    try {
      sendEvent(emitter, "error", Map.of("code", code, "message", message));
    } catch (IOException ioException) {
      log.warn("Failed to emit QA error event", ioException);
    } finally {
      emitter.complete();
    }
  }

  private void sendEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
    emitter.send(
        SseEmitter.event().name(eventName).data(writeJson(payload), MediaType.APPLICATION_JSON));
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new DifyOperationException(
          "QA_STREAM_SERIALIZATION_FAILED", "Failed to serialize QA stream payload", exception);
    }
  }

  private KnowledgeBase getAccessibleKnowledgeBase(UUID currentUserId, UUID knowledgeBaseId) {
    KnowledgeBase knowledgeBase =
        knowledgeBaseRepository
            .findById(knowledgeBaseId)
            .orElseThrow(
                () ->
                    new AppException(
                        HttpStatus.NOT_FOUND,
                        "KNOWLEDGE_BASE_NOT_FOUND",
                        "Knowledge base does not exist"));
    boolean owner = knowledgeBase.getOwner().getId().equals(currentUserId);
    boolean shared =
        knowledgeBaseShareRepository.existsByKnowledgeBase_IdAndSharedWith_Id(
            knowledgeBaseId, currentUserId);
    if (!owner && !shared) {
      throw new AppException(
          HttpStatus.FORBIDDEN,
          "KNOWLEDGE_BASE_ACCESS_DENIED",
          "You do not have access to this knowledge base");
    }
    return knowledgeBase;
  }
}
