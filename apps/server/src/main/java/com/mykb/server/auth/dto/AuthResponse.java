package com.mykb.server.auth.dto;

public record AuthResponse(String accessToken, UserProfileResponse user) {}
