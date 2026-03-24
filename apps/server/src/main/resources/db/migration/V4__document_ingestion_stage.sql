ALTER TABLE document_ingestion_tasks
    ADD COLUMN current_stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN ocr_engine VARCHAR(64);
