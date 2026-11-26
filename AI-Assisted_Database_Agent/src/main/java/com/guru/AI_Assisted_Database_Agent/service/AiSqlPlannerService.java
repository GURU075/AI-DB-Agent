package com.guru.AI_Assisted_Database_Agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guru.AI_Assisted_Database_Agent.ai.PromptBuilder;
import com.guru.AI_Assisted_Database_Agent.model.DbType;
import com.guru.AI_Assisted_Database_Agent.model.SessionState;
import com.guru.AI_Assisted_Database_Agent.model.SqlPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calls the LLM (via Spring AI ChatClient) and parses the response as a {@link SqlPlan}.
 *
 * The model is instructed (via system prompt) to output ONLY valid JSON
 * matching the SqlPlan schema — the Java layer then validates that plan.
 */
@Service
public class AiSqlPlannerService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiSqlPlannerService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Send the user's question to the LLM and get back a structured SqlPlan.
     *
     * @param question    natural language question from the user
     * @param state       current session state (dialect + schema)
     * @param adminMode   whether DDL is allowed
     * @return parsed SqlPlan
     * @throws Exception if LLM call fails or JSON is unparseable
     */
    public SqlPlan plan(String question, SessionState state, boolean adminMode) throws Exception {
        if (PromptInjectionGuard.isSuspicious(question)
                || PromptInjectionGuard.isSuspicious(state.getSchemaSummary())
                || state.getChatHistory().stream().anyMatch(PromptInjectionGuard::isSuspicious)) {
            throw new IllegalArgumentException("Untrusted context contains a prompt injection pattern.");
        }
        DbType dialect = state.getDbType();
        String schemaSummary = state.getSchemaSummary();

        String systemPrompt = PromptBuilder.buildSystemPrompt(dialect, schemaSummary, adminMode);

        // Build a short history context (last 6 lines)
        List<String> history = state.getChatHistory();
        String historyContext = history.size() > 6
                ? String.join("\n", history.subList(history.size() - 6, history.size()))
                : String.join("\n", history);

        String userMsg = PromptBuilder.buildUserMessage(question, historyContext);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMsg)
        ));

        String rawJson = chatClient.prompt(prompt)
                .call()
                .content();

        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("The AI provider returned an empty response.");
        }

        // Strip markdown fences if model wrapped JSON in ```json ... ```
        String cleaned = rawJson.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```(?:json)?", "").replaceAll("```", "").trim();
        }

        return objectMapper.readValue(cleaned, SqlPlan.class);
    }
}
