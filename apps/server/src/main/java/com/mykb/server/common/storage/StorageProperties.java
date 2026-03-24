package com.mykb.server.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

  public enum StorageType {
    LOCAL,
    MINIO
  }

  private StorageType type = StorageType.LOCAL;
  private String bucket = "mykb-documents";
  private String localBaseDir = ".runtime/storage";
  private String minioEndpoint = "http://127.0.0.1:9000";
  private String minioAccessKey = "minioadmin";
  private String minioSecretKey = "minioadmin";
  private long maxUploadSizeBytes = 25L * 1024L * 1024L;

  public StorageType getType() {
    return type;
  }

  public void setType(StorageType type) {
    this.type = type;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getLocalBaseDir() {
    return localBaseDir;
  }

  public void setLocalBaseDir(String localBaseDir) {
    this.localBaseDir = localBaseDir;
  }

  public String getMinioEndpoint() {
    return minioEndpoint;
  }

  public void setMinioEndpoint(String minioEndpoint) {
    this.minioEndpoint = minioEndpoint;
  }

  public String getMinioAccessKey() {
    return minioAccessKey;
  }

  public void setMinioAccessKey(String minioAccessKey) {
    this.minioAccessKey = minioAccessKey;
  }

  public String getMinioSecretKey() {
    return minioSecretKey;
  }

  public void setMinioSecretKey(String minioSecretKey) {
    this.minioSecretKey = minioSecretKey;
  }

  public long getMaxUploadSizeBytes() {
    return maxUploadSizeBytes;
  }

  public void setMaxUploadSizeBytes(long maxUploadSizeBytes) {
    this.maxUploadSizeBytes = maxUploadSizeBytes;
  }
}
