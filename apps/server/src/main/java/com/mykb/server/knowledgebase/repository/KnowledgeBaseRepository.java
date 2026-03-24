package com.mykb.server.knowledgebase.repository;

import com.mykb.server.knowledgebase.entity.KnowledgeBase;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {

  boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);

  @Query(
      """
            select distinct kb
            from KnowledgeBase kb
            left join KnowledgeBaseShare share on share.knowledgeBase = kb
            where kb.owner.id = :userId or share.sharedWith.id = :userId
            order by kb.createdAt desc
            """)
  List<KnowledgeBase> findAccessibleByUserId(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select kb from KnowledgeBase kb where kb.id = :knowledgeBaseId")
  Optional<KnowledgeBase> findByIdForUpdate(UUID knowledgeBaseId);
}
