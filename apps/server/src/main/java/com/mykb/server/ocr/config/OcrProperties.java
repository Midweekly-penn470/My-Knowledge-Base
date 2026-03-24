package com.mykb.server.ocr.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

  private boolean enabled = false;
  private String baseUrl = "http://127.0.0.1:8090";
  private Duration timeout = Duration.ofSeconds(30);
  private String pdfContentType = "application/pdf";

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

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public String getPdfContentType() {
    return pdfContentType;
  }

  public void setPdfContentType(String pdfContentType) {
    this.pdfContentType = pdfContentType;
  }
}
