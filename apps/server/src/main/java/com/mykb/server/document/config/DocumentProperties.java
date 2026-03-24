package com.mykb.server.document.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document")
public class DocumentProperties {

  private List<String> allowedFileExtensions = List.of("pdf", "md", "markdown", "doc", "docx");

  public List<String> getAllowedFileExtensions() {
    return allowedFileExtensions;
  }

  public void setAllowedFileExtensions(List<String> allowedFileExtensions) {
    this.allowedFileExtensions =
        allowedFileExtensions == null ? List.of() : List.copyOf(allowedFileExtensions);
  }
}
