package com.mykb.server.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;

public class MinioObjectStorageService implements ObjectStorageService {

  private final StorageProperties properties;
  private final MinioClient minioClient;

  public MinioObjectStorageService(StorageProperties properties, MinioClient minioClient) {
    this.properties = properties;
    this.minioClient = minioClient;
  }

  @Override
  public StoredObject store(
      String objectKey, InputStream inputStream, long size, String contentType) {
    try {
      ensureBucketExists(properties.getBucket());
      minioClient.putObject(
          PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(
                  inputStream, size, -1)
              .contentType(
                  contentType == null || contentType.isBlank()
                      ? "application/octet-stream"
                      : contentType)
              .build());
      return new StoredObject(
          StorageProperties.StorageType.MINIO.name(), properties.getBucket(), objectKey);
    } catch (Exception exception) {
      throw new StorageOperationException("Failed to upload file to MinIO", exception);
    }
  }

  @Override
  public byte[] read(String bucket, String objectKey) {
    try (InputStream inputStream =
        minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
      return inputStream.readAllBytes();
    } catch (Exception exception) {
      throw new StorageOperationException("Failed to read file from MinIO", exception);
    }
  }

  @Override
  public void delete(String bucket, String objectKey) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw new StorageOperationException("Failed to delete file from MinIO", exception);
    }
  }

  private void ensureBucketExists(String bucket) throws Exception {
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }
}
