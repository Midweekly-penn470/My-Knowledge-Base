package com.mykb.server.knowledgebase.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseShareRequest(@NotBlank @Email @Size(max = 128) String targetEmail) {}
