package com.mykb.server.knowledgebase.repository;

import com.mykb.server.knowledgebase.entity.KnowledgeBaseShare;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseShareRepository extends JpaRepository<KnowledgeBaseShare, UUID> {

  boolean existsByKnowledgeBase_IdAndSharedWith_Id(UUID knowledgeBaseId, UUID sharedWithId);

  Optional<KnowledgeBaseShare> findByKnowledgeBase_IdAndSharedWith_Id(
      UUID knowledgeBaseId, UUID sharedWithId);
}
