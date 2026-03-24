package com.mykb.server.dify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykb.server.dify.client.DifyClient;
import com.mykb.server.dify.client.HttpDifyClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DifyConfig {

  @Bean
  @ConditionalOnMissingBean(DifyClient.class)
  public DifyClient difyClient(DifyProperties properties, ObjectMapper objectMapper) {
    String normalizedBaseUrl = normalizeBaseUrl(properties.getBaseUrl());
    RestClient restClient =
        RestClient.builder()
            .baseUrl(normalizedBaseUrl)
            .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
            .build();
    return new HttpDifyClient(normalizedBaseUrl, restClient, objectMapper, properties);
  }

  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://127.0.0.1:8088/v1";
    }
    String normalized =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    if (normalized.endsWith("/v1")) {
      return normalized;
    }
    return normalized + "/v1";
  }
}
