package com.mykb.server.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QaStreamRequest(
    @NotBlank(message = "query must not be blank")
        @Size(max = 2000, message = "query must not exceed 2000 characters")
        String query) {}
