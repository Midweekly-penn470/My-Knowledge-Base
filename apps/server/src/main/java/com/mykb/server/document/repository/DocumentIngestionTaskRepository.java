package com.mykb.server.document.repository;

import com.mykb.server.document.entity.DocumentIngestionTask;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionTaskRepository
    extends JpaRepository<DocumentIngestionTask, UUID> {

  List<DocumentIngestionTask> findByDocument_KnowledgeBase_IdOrderByCreatedAtDesc(
      UUID knowledgeBaseId);

  List<DocumentIngestionTask> findByDocument_IdOrderByCreatedAtDesc(UUID documentId);

  boolean existsByDocument_IdAndStatusIn(
      UUID documentId, Collection<DocumentIngestionTask.TaskStatus> statuses);

  @EntityGraph(attributePaths = {"document", "document.knowledgeBase"})
  Optional<DocumentIngestionTask> findDetailedById(UUID taskId);
}
