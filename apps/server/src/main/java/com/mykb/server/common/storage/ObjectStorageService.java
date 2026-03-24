package com.mykb.server.common.storage;

import java.io.InputStream;

public interface ObjectStorageService {

  StoredObject store(String objectKey, InputStream inputStream, long size, String contentType);

  byte[] read(String bucket, String objectKey);

  void delete(String bucket, String objectKey);
}
