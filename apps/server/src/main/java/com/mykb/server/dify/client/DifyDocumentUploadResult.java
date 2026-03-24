package com.mykb.server.dify.client;

public record DifyDocumentUploadResult(String documentId, String batchId, String indexingStatus) {}
