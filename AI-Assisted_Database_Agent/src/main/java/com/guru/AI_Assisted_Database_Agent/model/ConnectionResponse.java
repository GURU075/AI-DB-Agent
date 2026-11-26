package com.guru.AI_Assisted_Database_Agent.model;

public record ConnectionResponse(
        boolean success,
        String sessionId,
        String dbType,
        String dbVersion,
        String message,
        SchemaInfo schema
) {}

