package com.mykb.server.dify.client;

public record DifyIndexingStatusResult(
    String indexingStatus, String errorMessage, Integer completedSegments, Integer totalSegments) {}
