package com.guru.AI_Assisted_Database_Agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guru.AI_Assisted_Database_Agent.model.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class AiAssistedDatabaseAgentApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void apiErrorTimestampSerializesWithApplicationObjectMapper() throws Exception {
		ApiErrorResponse response = new ApiErrorResponse(
				"TEST_ERROR",
				"Safe message",
				Instant.parse("2026-08-26T10:00:00Z")
		);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

		assertThat(json.get("timestamp").asText()).isEqualTo("2026-08-26T10:00:00Z");
	}

}
