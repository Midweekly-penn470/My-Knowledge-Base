package com.mykb.server.dify.client;

public record DifyCompletionRequest(
    String query, String context, String knowledgeBaseName, String userId) {}
