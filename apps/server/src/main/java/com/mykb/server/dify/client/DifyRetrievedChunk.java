package com.mykb.server.dify.client;

public record DifyRetrievedChunk(
    String documentId, String segmentId, String documentName, String content, Double score) {}
