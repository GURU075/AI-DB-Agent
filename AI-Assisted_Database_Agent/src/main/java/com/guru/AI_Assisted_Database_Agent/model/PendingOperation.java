package com.guru.AI_Assisted_Database_Agent.model;

import java.time.Instant;

public record PendingOperation(String operationId, SqlPlan plan, Instant createdAt) {}

