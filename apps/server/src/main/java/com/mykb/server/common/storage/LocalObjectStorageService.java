package com.mykb.server.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalObjectStorageService implements ObjectStorageService {

  private final StorageProperties properties;

  public LocalObjectStorageService(StorageProperties properties) {
    this.properties = properties;
  }

  @Override
  public StoredObject store(
      String objectKey, InputStream inputStream, long size, String contentType) {
    Path objectPath = resolvePath(properties.getBucket(), objectKey);

    try {
      Files.createDirectories(objectPath.getParent());
      Files.copy(inputStream, objectPath, StandardCopyOption.REPLACE_EXISTING);
      return new StoredObject(
          StorageProperties.StorageType.LOCAL.name(), properties.getBucket(), objectKey);
    } catch (IOException exception) {
      throw new StorageOperationException("Failed to persist file to local storage", exception);
    }
  }

  @Override
  public byte[] read(String bucket, String objectKey) {
    try {
      return Files.readAllBytes(resolvePath(bucket, objectKey));
    } catch (IOException exception) {
      throw new StorageOperationException("Failed to read file from local storage", exception);
    }
  }

  @Override
  public void delete(String bucket, String objectKey) {
    try {
      Files.deleteIfExists(resolvePath(bucket, objectKey));
    } catch (IOException exception) {
      throw new StorageOperationException("Failed to delete file from local storage", exception);
    }
  }

  private Path resolvePath(String bucket, String objectKey) {
    Path rootPath = Path.of(properties.getLocalBaseDir()).toAbsolutePath().normalize();
    Path bucketPath = rootPath.resolve(bucket).normalize();
    Path objectPath = bucketPath.resolve(objectKey).normalize();
    if (!objectPath.startsWith(bucketPath)) {
      throw new StorageOperationException("Resolved storage path is outside bucket root", null);
    }
    return objectPath;
  }
}
