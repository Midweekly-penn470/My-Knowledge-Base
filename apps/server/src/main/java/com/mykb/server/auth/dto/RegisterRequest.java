package com.mykb.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 32) String username,
    @NotBlank @Email @Size(max = 128) String email,
    @NotBlank @Size(min = 8, max = 64) String password) {}
