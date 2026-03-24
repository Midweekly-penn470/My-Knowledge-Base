package com.mykb.server.knowledgebase.controller;

import com.mykb.server.common.api.ApiResponse;
import com.mykb.server.common.security.AuthenticatedUser;
import com.mykb.server.knowledgebase.dto.KnowledgeBaseCreateRequest;
import com.mykb.server.knowledgebase.dto.KnowledgeBaseResponse;
import com.mykb.server.knowledgebase.dto.KnowledgeBaseShareRequest;
import com.mykb.server.knowledgebase.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

  private final KnowledgeBaseService knowledgeBaseService;

  public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
    this.knowledgeBaseService = knowledgeBaseService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody KnowledgeBaseCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(knowledgeBaseService.create(user, request)));
  }

  @GetMapping
  public ApiResponse<List<KnowledgeBaseResponse>> list(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(knowledgeBaseService.listAccessible(user));
  }

  @GetMapping("/{knowledgeBaseId}")
  public ApiResponse<KnowledgeBaseResponse> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID knowledgeBaseId) {
    return ApiResponse.success(knowledgeBaseService.getDetail(user, knowledgeBaseId));
  }

  @PostMapping("/{knowledgeBaseId}/shares")
  public ApiResponse<KnowledgeBaseResponse> share(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID knowledgeBaseId,
      @Valid @RequestBody KnowledgeBaseShareRequest request) {
    return ApiResponse.success(knowledgeBaseService.share(user, knowledgeBaseId, request));
  }
}
