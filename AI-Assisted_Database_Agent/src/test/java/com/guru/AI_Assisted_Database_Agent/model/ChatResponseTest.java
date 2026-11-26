package com.guru.AI_Assisted_Database_Agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void confirmationResponseHasStableDiscriminatorAndOperationId() throws Exception {
        SqlPlan plan = new SqlPlan(
                "UPDATE", "POSTGRES", "UPDATE customers SET name = :name WHERE id = :id",
                Map.of("name", "Ada", "id", 1), true, true, "Update customer"
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(
                ChatResponse.pendingConfirmation("Review", plan, "operation-1")
        ));

        assertThat(json.get("type").asText()).isEqualTo("confirmation_required");
        assertThat(json.get("operationId").asText()).isEqualTo("operation-1");
        assertThat(json.get("sqlPlan").get("sql").asText()).contains("UPDATE customers");
    }

    @Test
    void errorResponseDoesNotRequireResultFields() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(ChatResponse.error("Safe message")));

        assertThat(json.get("type").asText()).isEqualTo("error");
        assertThat(json.get("errorMessage").asText()).isEqualTo("Safe message");
        assertThat(json.get("rows").isArray()).isTrue();
    }
}

