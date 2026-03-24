package com.mykb.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykb.server.dify.client.DifyClient;
import com.mykb.server.dify.client.DifyDatasetResult;
import com.mykb.server.dify.client.DifyDocumentUploadResult;
import com.mykb.server.dify.client.DifyIndexingStatusResult;
import com.mykb.server.dify.client.DifyOperationException;
import com.mykb.server.ocr.client.OcrClient;
import com.mykb.server.ocr.client.OcrExtractResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DocumentModuleIntegrationTest.DocumentModuleTestConfig.class)
class DocumentModuleIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void ownerCanUploadDocumentAndQueryTaskStatus() throws Exception {
    AuthContext owner = register("doc-owner", "doc-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "document-kb");

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "handbook.md",
            MediaType.TEXT_PLAIN_VALUE,
            "knowledge-base-content".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(file)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.document.originalFilename").value("handbook.md"))
        .andExpect(jsonPath("$.data.document.processingStatus").value("QUEUED"))
        .andExpect(jsonPath("$.data.ingestionTask.status").value("PENDING"))
        .andExpect(jsonPath("$.data.ingestionTask.currentStage").value("QUEUED"));

    waitForTaskStatus(owner.token(), knowledgeBaseId, "SUCCEEDED");

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].storageProvider").value("LOCAL"))
        .andExpect(jsonPath("$.data[0].processingStatus").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].difyDocumentId").value("dify-doc-handbook.md"));

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].taskType").value("DOCUMENT_INGESTION"))
        .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].currentStage").value("COMPLETED"))
        .andExpect(jsonPath("$.data[0].externalBatchId").value("batch-handbook.md"))
        .andExpect(jsonPath("$.data[0].ocrEngine").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.difyDatasetId").value("dataset-document-kb"));
  }

  @Test
  void ownerCanUploadPdfAndTriggerOcrPath() throws Exception {
    AuthContext owner = register("pdf-owner", "pdf-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "pdf-kb");

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "scanned.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "fake-pdf-content".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(file)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.ingestionTask.currentStage").value("QUEUED"));

    waitForTaskStatus(owner.token(), knowledgeBaseId, "SUCCEEDED");

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].processingStatus").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].difyDocumentId").value("dify-text-scanned.pdf"));

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].currentStage").value("COMPLETED"))
        .andExpect(jsonPath("$.data[0].externalBatchId").value("batch-text-scanned.pdf"))
        .andExpect(jsonPath("$.data[0].ocrEngine").value("stub-ocr"));
  }

  @Test
  void unsupportedFileTypeShouldBeRejectedBeforeStorage() throws Exception {
    AuthContext owner = register("type-owner", "type-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "type-kb");

    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "notes.txt",
                        MediaType.TEXT_PLAIN_VALUE,
                        "plain-text".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
  }
  @Test
  void sharedViewerCannotUploadDocument() throws Exception {
    AuthContext owner = register("owner-share", "owner-share@example.com");
    AuthContext viewer = register("viewer-share", "viewer-share@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "shared-kb");
    shareKnowledgeBase(owner.token(), knowledgeBaseId, viewer.email());

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "viewer.md",
            MediaType.TEXT_PLAIN_VALUE,
            "viewer-content".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(file)
                .header("Authorization", "Bearer " + viewer.token()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_OWNER_ONLY"));
  }

  @Test
  void duplicateDocumentShouldReturnConflict() throws Exception {
    AuthContext owner = register("dup-owner", "dup-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "dup-kb");

    upload(
        owner.token(), knowledgeBaseId, "duplicate.md", MediaType.TEXT_PLAIN_VALUE, "same-body");
    waitForTaskStatus(owner.token(), knowledgeBaseId, "SUCCEEDED");

    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "duplicate.md",
                        MediaType.TEXT_PLAIN_VALUE,
                        "same-body".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_EXISTS"));
  }

  @Test
  void ownerCanRetryFailedTaskAndEventuallySucceed() throws Exception {
    AuthContext owner = register("retry-owner", "retry-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "retry-kb");

    upload(owner.token(), knowledgeBaseId, "retry.md", MediaType.TEXT_PLAIN_VALUE, "retry-body");
    waitForTaskStatus(owner.token(), knowledgeBaseId, "FAILED");

    String failedTaskId = latestTaskId(owner.token(), knowledgeBaseId);

    mockMvc
        .perform(
            post(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks/{taskId}/retry",
                    knowledgeBaseId,
                    failedTaskId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.currentStage").value("QUEUED"));

    waitForTaskStatus(owner.token(), knowledgeBaseId, "SUCCEEDED");

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].processingStatus").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].difyDocumentId").value("dify-doc-retry.md"));

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[1].status").value("FAILED"))
        .andExpect(jsonPath("$.data[1].failedStage").value("DIFY_UPLOAD"));
  }

  @Test
  void indexingFailedDocumentCannotBeRetriedOrDeleted() throws Exception {
    AuthContext owner = register("index-owner", "index-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "index-kb");

    upload(owner.token(), knowledgeBaseId, "indexing.md", MediaType.TEXT_PLAIN_VALUE, "index-body");
    waitForTaskStatus(owner.token(), knowledgeBaseId, "FAILED");

    String taskId = latestTaskId(owner.token(), knowledgeBaseId);
    String documentId = latestDocumentId(owner.token(), knowledgeBaseId);

    mockMvc
        .perform(
            post(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks/{taskId}/retry",
                    knowledgeBaseId,
                    taskId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TASK_RETRY_INDEXING_FAILED"));

    mockMvc
        .perform(
            delete(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
                    knowledgeBaseId,
                    documentId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_DELETE_INDEXING_FAILED"));
  }
  @Test
  void ownerCanDeleteFailedDocumentAndUploadSameFileAgain() throws Exception {
    AuthContext owner = register("delete-owner", "delete-owner@example.com");
    String knowledgeBaseId = createKnowledgeBase(owner.token(), "delete-kb");

    upload(owner.token(), knowledgeBaseId, "delete.md", MediaType.TEXT_PLAIN_VALUE, "delete-body");
    waitForTaskStatus(owner.token(), knowledgeBaseId, "FAILED");

    String documentId = latestDocumentId(owner.token(), knowledgeBaseId);

    mockMvc
        .perform(
            delete(
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
                    knowledgeBaseId,
                    documentId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    upload(owner.token(), knowledgeBaseId, "delete.md", MediaType.TEXT_PLAIN_VALUE, "delete-body");
    waitForTaskStatus(owner.token(), knowledgeBaseId, "SUCCEEDED");
  }

  private void waitForTaskStatus(String token, String knowledgeBaseId, String expectedStatus)
      throws Exception {
    for (int attempt = 0; attempt < 30; attempt++) {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                      .header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andReturn();
      JsonNode tasks = readData(result);
      if (tasks.isArray()
          && tasks.size() > 0
          && expectedStatus.equals(tasks.get(0).get("status").asText())) {
        return;
      }
      Thread.sleep(100L);
    }
    throw new AssertionError(
        "Document ingestion task did not reach " + expectedStatus + " within timeout");
  }

  private String latestTaskId(String token, String knowledgeBaseId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/knowledge-bases/{knowledgeBaseId}/ingestion-tasks", knowledgeBaseId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return readData(result).get(0).get("id").asText();
  }

  private String latestDocumentId(String token, String knowledgeBaseId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return readData(result).get(0).get("id").asText();
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

  private void upload(
      String token, String knowledgeBaseId, String filename, String contentType, String body)
      throws Exception {
    mockMvc
        .perform(
            multipart(
                    HttpMethod.POST,
                    "/api/v1/knowledge-bases/{knowledgeBaseId}/documents",
                    knowledgeBaseId)
                .file(
                    new MockMultipartFile(
                        "file", filename, contentType, body.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated());
  }

  private JsonNode readData(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
  }

  private record AuthContext(String token, String email) {}

  @TestConfiguration
  static class DocumentModuleTestConfig {

    @Bean
    @Primary
    DifyClient difyClient() {
      return new StubDifyClient();
    }

    @Bean
    @Primary
    OcrClient ocrClient() {
      return (filename, contentType, fileBytes) ->
          new OcrExtractResult("extracted text from " + filename, "stub-ocr");
    }
  }

  static class StubDifyClient implements DifyClient {

    private final Map<String, Integer> uploadAttempts = new ConcurrentHashMap<>();
    private final Set<String> failFirstUploadFiles = Set.of("retry.md", "delete.md");
    private final Set<String> indexingFailureFiles = Set.of("indexing.md");

    @Override
    public DifyDatasetResult createDataset(String name, String description) {
      return new DifyDatasetResult("dataset-" + name);
    }

    @Override
    public DifyDocumentUploadResult uploadDocument(
        String datasetId, String filename, String contentType, byte[] fileBytes) {
      int attempt = uploadAttempts.merge(filename, 1, Integer::sum);
      if (failFirstUploadFiles.contains(filename) && attempt == 1) {
        throw new DifyOperationException(
            "DIFY_UPLOAD_DOCUMENT_FAILED", "stubbed upload failure for " + filename);
      }
      return new DifyDocumentUploadResult(
          "dify-doc-" + filename, "batch-" + filename, "waiting");
    }

    @Override
    public DifyDocumentUploadResult createTextDocument(String datasetId, String name, String text) {
      return new DifyDocumentUploadResult("dify-text-" + name, "batch-text-" + name, "waiting");
    }

    @Override
    public DifyIndexingStatusResult getIndexingStatus(String datasetId, String batchId) {
      if (indexingFailureFiles.contains(batchId.replace("batch-", ""))) {
        return new DifyIndexingStatusResult(
            "error", "stubbed indexing failure for " + batchId, 1, 1);
      }
      return new DifyIndexingStatusResult("completed", null, 1, 1);
    }
  }
}