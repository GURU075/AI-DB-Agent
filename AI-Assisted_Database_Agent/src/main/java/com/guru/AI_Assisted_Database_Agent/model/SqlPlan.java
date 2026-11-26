package com.guru.AI_Assisted_Database_Agent.model;

import java.util.Map;

public record SqlPlan(
        String intent,              // SELECT, INSERT, UPDATE, DELETE, DDL, PROCEDURE_CALL
        String dialect,             // POSTGRES, ORACLE, MYSQL
        String sql,
        Map<String, Object> params,
        boolean destructive,
        boolean requiresConfirmation,
        String reason
) {}