package com.mykb.server.dify.client;

public record DifyCompletionEvent(String event, String answer, String messageId, String taskId) {}
