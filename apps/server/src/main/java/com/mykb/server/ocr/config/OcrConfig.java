package com.mykb.server.ocr.config;

import com.mykb.server.ocr.client.HttpOcrClient;
import com.mykb.server.ocr.client.OcrClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OcrConfig {

  @Bean
  @ConditionalOnMissingBean(OcrClient.class)
  public OcrClient ocrClient(OcrProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    RestClient restClient =
        RestClient.builder()
            .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
            .requestFactory(requestFactory)
            .build();
    return new HttpOcrClient(restClient);
  }

  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://127.0.0.1:8090";
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
