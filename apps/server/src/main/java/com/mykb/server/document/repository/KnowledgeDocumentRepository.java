package com.mykb.server.document.repository;

import com.mykb.server.document.entity.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

  boolean existsByKnowledgeBase_IdAndOriginalFilenameIgnoreCaseAndSizeBytes(
      UUID knowledgeBaseId, String originalFilename, long sizeBytes);

  List<KnowledgeDocument> findByKnowledgeBase_IdOrderByCreatedAtDesc(UUID knowledgeBaseId);

  @EntityGraph(attributePaths = {"knowledgeBase", "uploader"})
  Optional<KnowledgeDocument> findDetailedById(UUID documentId);
}
