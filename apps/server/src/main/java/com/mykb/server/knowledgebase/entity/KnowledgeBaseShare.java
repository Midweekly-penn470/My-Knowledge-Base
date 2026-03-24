package com.mykb.server.knowledgebase.entity;

import com.mykb.server.auth.entity.UserAccount;
import com.mykb.server.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_base_shares")
public class KnowledgeBaseShare extends AuditableEntity {

  public enum AccessRole {
    VIEWER
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shared_with_id", nullable = false)
  private UserAccount sharedWith;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AccessRole accessRole = AccessRole.VIEWER;

  public KnowledgeBase getKnowledgeBase() {
    return knowledgeBase;
  }

  public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
    this.knowledgeBase = knowledgeBase;
  }

  public UserAccount getSharedWith() {
    return sharedWith;
  }

  public void setSharedWith(UserAccount sharedWith) {
    this.sharedWith = sharedWith;
  }

  public AccessRole getAccessRole() {
    return accessRole;
  }

  public void setAccessRole(AccessRole accessRole) {
    this.accessRole = accessRole;
  }
}
