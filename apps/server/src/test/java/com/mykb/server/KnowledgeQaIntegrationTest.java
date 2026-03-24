package com.mykb.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykb.server.dify.client.DifyClient;
import com.mykb.server.dify.client.DifyCompletionEvent;
import com.mykb.server.dify.client.DifyCompletionRequest;
import com.mykb.server.dify.client.DifyDatasetResult;
import com.mykb.server.dify.client.DifyDocumentUploadResult;
import com.mykb.server.dify.client.DifyIndexingStatusResult;
import com.mykb.server.dify.client.DifyOperationException;
import com.mykb.server.dify.client.DifyRetrievedChunk;
import com.mykb.server.knowledgebase.entity.KnowledgeBase;
import com.mykb.server.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(KnowledgeQaIntegrationTest.KnowledgeQaTestConfig.class)
class KnowledgeQaIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private KnowledgeBaseRepository knowledgeBaseRepository;

  @Test
  void ownerCanStreamAnswerWithSources() throws Exception {
    AuthContext owner = register("qa-owner", "qa-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "qa-kb");
    markDatasetReady(knowledgeBaseId, "dataset-qa-kb");

    String responseContent = streamQa(owner.token(), knowledgeBaseId, "What is the handbook rule?");

    assertThat(responseContent).contains("event:start");
    assertThat(responseContent).contains("event:sources");
    assertThat(responseContent.indexOf("handbook.md")).isLessThan(responseContent.indexOf("policy.md"));
    assertThat(responseContent).contains("event:message");
    assertThat(responseContent).contains("The handbook says");
    assertThat(responseContent).contains("event:done");
    assertThat(responseContent).contains("\"refusal\":false");
  }

  @Test
  void sharedViewerCanAskQuestion() throws Exception {
    AuthContext owner = register("qa-share-owner", "qa-share-owner@example.com");
    AuthContext viewer = register("qa-share-viewer", "qa-share-viewer@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "shared-qa-kb");
    markDatasetReady(knowledgeBaseId, "dataset-shared-qa-kb");
    shareKnowledgeBase(owner.token(), knowledgeBaseId, viewer.email());

    String responseContent = streamQa(viewer.token(), knowledgeBaseId, "Explain the shared policy");

    assertThat(responseContent).contains("event:start");
    assertThat(responseContent).contains("event:done");
    assertThat(responseContent).contains("\"refusal\":false");
  }

  @Test
  void emptyRetrievalShouldReturnRefusalMessage() throws Exception {
    AuthContext owner = register("qa-refusal-owner", "qa-refusal-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "qa-refusal-kb");
    markDatasetReady(knowledgeBaseId, "dataset-qa-refusal-kb");

    String responseContent = streamQa(owner.token(), knowledgeBaseId, "unknown-no-match");

    assertThat(responseContent).contains("event:sources");
    assertThat(responseContent).contains("event:message");
    assertThat(responseContent).contains("No sufficiently relevant knowledge snippets were found");
    assertThat(responseContent).contains("\"refusal\":true");
  }

  @Test
  void knowledgeBaseNotReadyShouldReturnConflict() throws Exception {
    AuthContext owner = register("qa-not-ready-owner", "qa-not-ready-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "qa-not-ready-kb");

    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/qa/stream", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", "any question"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"code\":\"KNOWLEDGE_BASE_NOT_READY\"}", false));
  }

  @Test
  void unauthorizedViewerShouldBeDenied() throws Exception {
    AuthContext owner = register("qa-deny-owner", "qa-deny-owner@example.com");
    AuthContext viewer = register("qa-deny-viewer", "qa-deny-viewer@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "qa-deny-kb");
    markDatasetReady(knowledgeBaseId, "dataset-qa-deny-kb");

    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/qa/stream", knowledgeBaseId)
                .header("Authorization", "Bearer " + viewer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", "any question"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"code\":\"KNOWLEDGE_BASE_ACCESS_DENIED\"}", false));
  }

  @Test
  void streamFailureShouldEmitErrorEvent() throws Exception {
    AuthContext owner = register("qa-stream-owner", "qa-stream-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "qa-stream-kb");
    markDatasetReady(knowledgeBaseId, "dataset-qa-stream-kb");

    String responseContent = streamQa(owner.token(), knowledgeBaseId, "stream-error");

    assertThat(responseContent).contains("event:start");
    assertThat(responseContent).contains("event:error");
    assertThat(responseContent).contains("QA_STREAM_FAILED");
    assertThat(responseContent).contains("stubbed QA stream failure");
  }

  private String streamQa(String token, String knowledgeBaseId, String query) throws Exception {
    MvcResult asyncResult =
        mockMvc
            .perform(
                post("/api/v1/knowledge-bases/{knowledgeBaseId}/qa/stream", knowledgeBaseId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("query", query))))
            .andExpect(request().asyncStarted())
            .andReturn();

    return mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private AuthContext register(String username, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "username", username,
                                "email", email,
                                "password", "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode data = readData(result);
    return new AuthContext(data.get("accessToken").asText(), email);
  }

  private String createKnowledgeBase(String token, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/knowledge-bases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", name, "description", "owner knowledge base"))))
            .andExpect(status().isCreated())
            .andReturn();

    return readData(result).get("id").asText();
  }

  private void shareKnowledgeBase(String token, String knowledgeBaseId, String targetEmail)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/shares", knowledgeBaseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetEmail", targetEmail))))
        .andExpect(status().isOk());
  }

  private void markDatasetReady(String knowledgeBaseId, String datasetId) {
    KnowledgeBase knowledgeBase =
        knowledgeBaseRepository.findById(java.util.UUID.fromString(knowledgeBaseId)).orElseThrow();
    knowledgeBase.setDifyDatasetId(datasetId);
    knowledgeBaseRepository.save(knowledgeBase);
  }

  private JsonNode readData(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
  }

  private record AuthContext(String token, String email) {}

  @TestConfiguration
  static class KnowledgeQaTestConfig {

    @Bean
    @Primary
    DifyClient difyClient() {
      return new DifyClient() {
        @Override
        public DifyDatasetResult createDataset(String name, String description) {
          return new DifyDatasetResult("dataset-" + name);
        }

        @Override
        public DifyDocumentUploadResult uploadDocument(
            String datasetId, String filename, String contentType, byte[] fileBytes) {
          return new DifyDocumentUploadResult("doc-" + filename, "batch-" + filename, "waiting");
        }

        @Override
        public DifyDocumentUploadResult createTextDocument(
            String datasetId, String name, String text) {
          return new DifyDocumentUploadResult("doc-" + name, "batch-" + name, "waiting");
        }

        @Override
        public DifyIndexingStatusResult getIndexingStatus(String datasetId, String batchId) {
          return new DifyIndexingStatusResult("completed", null, 1, 1);
        }

        @Override
        public List<DifyRetrievedChunk> retrieveChunks(String datasetId, String query) {
          if (query.contains("unknown-no-match")) {
            return List.of();
          }
          return List.of(
              new DifyRetrievedChunk(
                  "doc-null-score",
                  "seg-0",
                  "no-score.md",
                  "A chunk without score should not outrank scored chunks.",
                  null),
              new DifyRetrievedChunk(
                  "doc-handbook",
                  "seg-1",
                  "handbook.md",
                  "The handbook says every answer should stay grounded in the uploaded sources.",
                  0.92d),
              new DifyRetrievedChunk(
                  "doc-policy",
                  "seg-2",
                  "policy.md",
                  "Shared viewers can ask questions but cannot upload new documents.",
                  0.81d));
        }

        @Override
        public void streamCompletion(
            DifyCompletionRequest request, Consumer<DifyCompletionEvent> eventConsumer) {
          if (request.query().contains("stream-error")) {
            throw new DifyOperationException("QA_STREAM_FAILED", "stubbed QA stream failure");
          }
          eventConsumer.accept(
              new DifyCompletionEvent("message", "The handbook says ", "msg-1", "task-1"));
          eventConsumer.accept(
              new DifyCompletionEvent(
                  "message", "answers must stay grounded in uploaded sources.", "msg-1", "task-1"));
        }
      };
    }
  }
}