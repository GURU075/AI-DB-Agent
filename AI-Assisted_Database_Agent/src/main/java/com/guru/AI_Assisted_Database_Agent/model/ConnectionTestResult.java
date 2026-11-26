package com.guru.AI_Assisted_Database_Agent.model;

public record ConnectionTestResult(
        boolean success,
        String databaseProductName,
        String databaseProductVersion,
        String message
) {}
