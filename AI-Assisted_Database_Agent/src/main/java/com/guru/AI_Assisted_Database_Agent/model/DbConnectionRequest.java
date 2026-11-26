package com.guru.AI_Assisted_Database_Agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DbConnectionRequest(
        @NotNull DbType dbType,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotBlank String database,
        @NotBlank String username,
        @NotBlank String password,
        String schema,
        boolean ssl
) {}
