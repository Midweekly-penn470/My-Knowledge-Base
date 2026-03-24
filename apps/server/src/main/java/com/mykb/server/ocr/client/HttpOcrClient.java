package com.mykb.server.ocr.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpOcrClient implements OcrClient {

  private final RestClient restClient;

  public HttpOcrClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public OcrExtractResult extractText(String filename, String contentType, byte[] fileBytes) {
    LinkedMultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
    multipartBody.add("file", new NamedByteArrayResource(filename, fileBytes));

    try {
      JsonNode body =
          restClient
              .post()
              .uri("/api/v1/ocr/extract")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(multipartBody)
              .retrieve()
              .body(JsonNode.class);
      String text = textValue(body, "text");
      if (text == null || text.isBlank()) {
        throw new OcrOperationException("OCR response missing extracted text");
      }
      return new OcrExtractResult(text, textValue(body, "engine"));
    } catch (RestClientResponseException exception) {
      throw new OcrOperationException(
          "OCR service request failed: status=%s, response=%s"
              .formatted(
                  exception.getStatusCode(), trimMessage(exception.getResponseBodyAsString())),
          exception);
    } catch (RestClientException exception) {
      throw new OcrOperationException("OCR service request failed", exception);
    }
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode valueNode = node.path(fieldName);
    if (valueNode.isMissingNode() || valueNode.isNull()) {
      return null;
    }
    return valueNode.asText();
  }

  private String trimMessage(String rawMessage) {
    if (rawMessage == null) {
      return "";
    }
    String normalized = rawMessage.replaceAll("\\s+", " ").trim();
    return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
  }

  private static final class NamedByteArrayResource extends ByteArrayResource {

    private final String filename;

    private NamedByteArrayResource(String filename, byte[] byteArray) {
      super(byteArray);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
