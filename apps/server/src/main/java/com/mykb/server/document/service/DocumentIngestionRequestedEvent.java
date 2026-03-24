package com.mykb.server.document.service;

import java.util.UUID;

public record DocumentIngestionRequestedEvent(UUID taskId) {}
