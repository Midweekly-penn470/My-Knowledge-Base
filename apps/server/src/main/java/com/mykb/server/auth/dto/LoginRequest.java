package com.mykb.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String identity, @NotBlank String password) {}
