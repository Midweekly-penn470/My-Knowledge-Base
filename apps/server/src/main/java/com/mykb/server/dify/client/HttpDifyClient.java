package com.mykb.server.dify.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykb.server.dify.config.DifyProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpDifyClient implements DifyClient {

  private final String baseUrl;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final DifyProperties properties;
  private final HttpClient httpClient;

  public HttpDifyClient(
      String baseUrl, RestClient restClient, ObjectMapper objectMapper, DifyProperties properties) {
    this.baseUrl = baseUrl;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public DifyDatasetResult createDataset(String name, String description) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("description", description == null ? "" : description);
    payload.put("permission", properties.getDatasetPermission());
    payload.put("provider", properties.getProvider());
    payload.put("indexing_technique", properties.getIndexingTechnique());

    JsonNode body =
        execute(
            () ->
                restClient
                    .post()
                    .uri("/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class),
            "DIFY_CREATE_DATASET_FAILED");

    return new DifyDatasetResult(requireText(body, "id", "dataset id"));
  }

  @Override
  public DifyDocumentUploadResult uploadDocument(
      String datasetId, String filename, String contentType, byte[] fileBytes) {
    LinkedMultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
    multipartBody.add("data", new HttpEntity<>(buildUploadDataJson(), textHeaders()));
    multipartBody.add(
        "file",
        new HttpEntity<>(
            new ByteArrayResource(fileBytes) {
              @Override
              public String getFilename() {
                return filename;
              }
            },
            fileHeaders(contentType)));

    JsonNode body =
        execute(
            () ->
                restClient
                    .post()
                    .uri("/datasets/{datasetId}/document/create-by-file", datasetId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(JsonNode.class),
            "DIFY_UPLOAD_DOCUMENT_FAILED");

    JsonNode documentNode = body.path("document");
    return new DifyDocumentUploadResult(
        requireText(documentNode, "id", "document id"),
        requireText(body, "batch", "batch id"),
        textValue(documentNode, "indexing_status"));
  }

  @Override
  public DifyDocumentUploadResult createTextDocument(String datasetId, String name, String text) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("text", text);
    payload.put("indexing_technique", properties.getIndexingTechnique());
    payload.put("doc_form", "text_model");
    payload.put("process_rule", Map.of("mode", "automatic"));

    JsonNode body =
        execute(
            () ->
                restClient
                    .post()
                    .uri("/datasets/{datasetId}/document/create-by-text", datasetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class),
            "DIFY_CREATE_TEXT_DOCUMENT_FAILED");

    JsonNode documentNode = body.path("document");
    return new DifyDocumentUploadResult(
        requireText(documentNode, "id", "document id"),
        requireText(body, "batch", "batch id"),
        textValue(documentNode, "indexing_status"));
  }

  @Override
  public DifyIndexingStatusResult getIndexingStatus(String datasetId, String batchId) {
    JsonNode body =
        execute(
            () ->
                restClient
                    .get()
                    .uri(
                        "/datasets/{datasetId}/documents/{batchId}/indexing-status",
                        datasetId,
                        batchId)
                    .retrieve()
                    .body(JsonNode.class),
            "DIFY_GET_INDEXING_STATUS_FAILED");

    JsonNode firstStatusNode = body.path("data").path(0);
    return new DifyIndexingStatusResult(
        requireText(firstStatusNode, "indexing_status", "indexing status"),
        textValue(firstStatusNode, "error"),
        intValue(firstStatusNode, "completed_segments"),
        intValue(firstStatusNode, "total_segments"));
  }

  @Override
  public List<DifyRetrievedChunk> retrieveChunks(String datasetId, String query) {
    JsonNode body =
        execute(
            () ->
                restClient
                    .post()
                    .uri("/datasets/{datasetId}/retrieve", datasetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(JsonNode.class),
            "DIFY_RETRIEVE_CHUNKS_FAILED");

    List<DifyRetrievedChunk> chunks = new ArrayList<>();
    for (JsonNode recordNode : body.path("records")) {
      JsonNode segmentNode = recordNode.path("segment");
      JsonNode documentNode = recordNode.path("document");
      chunks.add(
          new DifyRetrievedChunk(
              textValue(documentNode, "id"),
              textValue(segmentNode, "id"),
              textValue(documentNode, "name"),
              firstNonBlank(textValue(segmentNode, "content"), textValue(recordNode, "content")),
              doubleValue(recordNode, "score")));
    }
    return chunks;
  }

  @Override
  public void streamCompletion(
      DifyCompletionRequest request, Consumer<DifyCompletionEvent> eventConsumer) {
    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/completion-messages"))
            .timeout(properties.getCompletionTimeout())
            .header("Authorization", "Bearer " + resolveCompletionApiKey())
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    buildCompletionRequestBody(request), StandardCharsets.UTF_8))
            .build();

    try {
      HttpResponse<InputStream> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() / 100 != 2) {
        throw new DifyOperationException(
            "DIFY_STREAM_COMPLETION_FAILED",
            "Dify completion request failed: status=%s, response=%s"
                .formatted(response.statusCode(), readResponseBody(response.body())));
      }
      streamEvents(response.body(), eventConsumer);
    } catch (IOException exception) {
      throw new DifyOperationException(
          "DIFY_STREAM_COMPLETION_FAILED", "Dify completion stream I/O failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DifyOperationException(
          "DIFY_STREAM_COMPLETION_INTERRUPTED",
          "Dify completion stream was interrupted",
          exception);
    }
  }

  private void streamEvents(InputStream responseBody, Consumer<DifyCompletionEvent> eventConsumer)
      throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("data:")) {
          continue;
        }
        String payload = line.substring("data:".length()).trim();
        if (payload.isBlank() || "[DONE]".equals(payload)) {
          continue;
        }
        JsonNode eventNode = objectMapper.readTree(payload);
        String eventType = textValue(eventNode, "event");
        if ("error".equals(eventType)) {
          throw new DifyOperationException(
              "DIFY_STREAM_COMPLETION_FAILED",
              firstNonBlank(
                  textValue(eventNode, "message"), "Dify completion stream returned an error"));
        }
        eventConsumer.accept(
            new DifyCompletionEvent(
                eventType,
                textValue(eventNode, "answer"),
                firstNonBlank(textValue(eventNode, "message_id"), textValue(eventNode, "id")),
                textValue(eventNode, "task_id")));
      }
    }
  }

  private String buildCompletionRequestBody(DifyCompletionRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("query", request.query());
    payload.put(
        "inputs",
        Map.of(
            "query", request.query(),
            "question", request.query(),
            "context", request.context(),
            "knowledge_base_name", request.knowledgeBaseName()));
    payload.put("response_mode", "streaming");
    payload.put("user", request.userId());
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new DifyOperationException(
          "DIFY_BUILD_REQUEST_FAILED", "Failed to serialize Dify completion request", exception);
    }
  }

  private String resolveCompletionApiKey() {
    String appApiKey = firstNonBlank(properties.getAppApiKey(), properties.getApiKey());
    if (appApiKey == null) {
      throw new DifyOperationException(
          "DIFY_APP_API_KEY_MISSING", "Dify app API key is required for completion streaming");
    }
    return appApiKey;
  }

  private String readResponseBody(InputStream inputStream) throws IOException {
    String rawBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    return trimMessage(rawBody);
  }

  private <T> T execute(DifySupplier<T> supplier, String errorCode) {
    try {
      return supplier.get();
    } catch (RestClientResponseException exception) {
      String message =
          "Dify API request failed: status=%s, response=%s"
              .formatted(
                  exception.getStatusCode(), trimMessage(exception.getResponseBodyAsString()));
      throw new DifyOperationException(errorCode, message, exception);
    } catch (RestClientException exception) {
      throw new DifyOperationException(errorCode, "Dify API request failed", exception);
    }
  }

  private String buildUploadDataJson() {
    Map<String, Object> processRule = Map.of("mode", "automatic");
    Map<String, Object> payload =
        Map.of(
            "indexing_technique", properties.getIndexingTechnique(), "process_rule", processRule);
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new DifyOperationException(
          "DIFY_BUILD_REQUEST_FAILED", "Failed to serialize Dify upload request", exception);
    }
  }

  private HttpHeaders textHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    return headers;
  }

  private HttpHeaders fileHeaders(String contentType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(
        contentType == null || contentType.isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(contentType));
    return headers;
  }

  private String requireText(JsonNode node, String fieldName, String fieldLabel) {
    String value = textValue(node, fieldName);
    if (value == null || value.isBlank()) {
      throw new DifyOperationException(
          "DIFY_INVALID_RESPONSE", "Dify response missing required " + fieldLabel);
    }
    return value;
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode valueNode = node.path(fieldName);
    if (valueNode.isMissingNode() || valueNode.isNull()) {
      return null;
    }
    return valueNode.asText();
  }

  private Integer intValue(JsonNode node, String fieldName) {
    JsonNode valueNode = node.path(fieldName);
    if (!valueNode.isNumber()) {
      return null;
    }
    return valueNode.asInt();
  }

  private Double doubleValue(JsonNode node, String fieldName) {
    JsonNode valueNode = node.path(fieldName);
    if (valueNode.isNumber()) {
      return valueNode.asDouble();
    }
    if (valueNode.isTextual()) {
      try {
        return Double.parseDouble(valueNode.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String trimMessage(String rawMessage) {
    if (rawMessage == null) {
      return "";
    }
    String normalized = rawMessage.replaceAll("\\s+", " ").trim();
    return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
  }

  @FunctionalInterface
  private interface DifySupplier<T> {

    T get();
  }
}
