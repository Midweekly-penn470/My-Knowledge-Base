package com.mykb.server.document.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentIngestionRequestedListener {

  private final DocumentIngestionWorkflowService workflowService;

  public DocumentIngestionRequestedListener(DocumentIngestionWorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(DocumentIngestionRequestedEvent event) {
    workflowService.ingestAsync(event.taskId());
  }
}
