CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases (id),
    uploader_id UUID NOT NULL REFERENCES user_accounts (id),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    size_bytes BIGINT NOT NULL,
    storage_provider VARCHAR(16) NOT NULL,
    storage_bucket VARCHAR(80) NOT NULL,
    storage_object_key VARCHAR(255) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    dify_document_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_knowledge_documents_kb_filename_size
    ON knowledge_documents (knowledge_base_id, LOWER(original_filename), size_bytes);

CREATE INDEX idx_knowledge_documents_kb_created_at
    ON knowledge_documents (knowledge_base_id, created_at DESC);

CREATE TABLE document_ingestion_tasks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_documents (id),
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_ingestion_tasks_document_created_at
    ON document_ingestion_tasks (document_id, created_at DESC);
