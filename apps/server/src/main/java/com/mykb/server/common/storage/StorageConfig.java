package com.mykb.server.common.storage;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

  @Bean
  public ObjectStorageService objectStorageService(StorageProperties properties) {
    return switch (properties.getType()) {
      case LOCAL -> new LocalObjectStorageService(properties);
      case MINIO ->
          new MinioObjectStorageService(
              properties,
              MinioClient.builder()
                  .endpoint(properties.getMinioEndpoint())
                  .credentials(properties.getMinioAccessKey(), properties.getMinioSecretKey())
                  .build());
    };
  }
}
