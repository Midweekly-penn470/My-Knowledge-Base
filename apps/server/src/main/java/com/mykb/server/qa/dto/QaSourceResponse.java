package com.mykb.server.qa.dto;

public record QaSourceResponse(
    String documentId, String segmentId, String documentName, String content, Double score) {}
