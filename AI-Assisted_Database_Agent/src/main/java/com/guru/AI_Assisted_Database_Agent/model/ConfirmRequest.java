package com.guru.AI_Assisted_Database_Agent.model;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
        @NotBlank String sessionId,
        @NotBlank String operationId,
        boolean confirm
) {}
