package com.mykb.server.common.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String username) {}
