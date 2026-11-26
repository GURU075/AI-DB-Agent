package com.guru.AI_Assisted_Database_Agent.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String userMessage
) {}