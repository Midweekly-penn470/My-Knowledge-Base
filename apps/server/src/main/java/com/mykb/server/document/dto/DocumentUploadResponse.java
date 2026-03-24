package com.mykb.server.document.dto;

public record DocumentUploadResponse(
    KnowledgeDocumentResponse document, IngestionTaskResponse ingestionTask) {}
