package com.mykb.server.dify.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dify")
public class DifyProperties {

  private boolean enabled = true;
  private String baseUrl = "http://127.0.0.1:8088/v1";
  private String apiKey;
  private String appApiKey;
  private String datasetPermission = "only_me";
  private String provider = "vendor";
  private String indexingTechnique = "high_quality";
  private Duration pollInterval = Duration.ofSeconds(2);
  private int maxPollAttempts = 30;
  private int retrievalTopK = 5;
  private double retrievalMinScore = 0.35d;
  private int qaContextMaxChars = 6000;
  private Duration completionTimeout = Duration.ofMinutes(2);
  private Duration qaSseTimeout = Duration.ofMinutes(2);
  private String qaRefusalMessage =
      "No sufficiently relevant knowledge snippets were found, so the system refuses to answer this question.";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getAppApiKey() {
    return appApiKey;
  }

  public void setAppApiKey(String appApiKey) {
    this.appApiKey = appApiKey;
  }

  public String getDatasetPermission() {
    return datasetPermission;
  }

  public void setDatasetPermission(String datasetPermission) {
    this.datasetPermission = datasetPermission;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getIndexingTechnique() {
    return indexingTechnique;
  }

  public void setIndexingTechnique(String indexingTechnique) {
    this.indexingTechnique = indexingTechnique;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  public int getMaxPollAttempts() {
    return maxPollAttempts;
  }

  public void setMaxPollAttempts(int maxPollAttempts) {
    this.maxPollAttempts = maxPollAttempts;
  }

  public int getRetrievalTopK() {
    return retrievalTopK;
  }

  public void setRetrievalTopK(int retrievalTopK) {
    this.retrievalTopK = retrievalTopK;
  }

  public double getRetrievalMinScore() {
    return retrievalMinScore;
  }

  public void setRetrievalMinScore(double retrievalMinScore) {
    this.retrievalMinScore = retrievalMinScore;
  }

  public int getQaContextMaxChars() {
    return qaContextMaxChars;
  }

  public void setQaContextMaxChars(int qaContextMaxChars) {
    this.qaContextMaxChars = qaContextMaxChars;
  }

  public Duration getCompletionTimeout() {
    return completionTimeout;
  }

  public void setCompletionTimeout(Duration completionTimeout) {
    this.completionTimeout = completionTimeout;
  }

  public Duration getQaSseTimeout() {
    return qaSseTimeout;
  }

  public void setQaSseTimeout(Duration qaSseTimeout) {
    this.qaSseTimeout = qaSseTimeout;
  }

  public String getQaRefusalMessage() {
    return qaRefusalMessage;
  }

  public void setQaRefusalMessage(String qaRefusalMessage) {
    this.qaRefusalMessage = qaRefusalMessage;
  }
}
