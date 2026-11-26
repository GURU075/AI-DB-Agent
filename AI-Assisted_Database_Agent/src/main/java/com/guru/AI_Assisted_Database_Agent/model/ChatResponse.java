package com.guru.AI_Assisted_Database_Agent.model;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String type,
        String answer,
        SqlPlan sqlPlan,
        List<String> columns,
        List<Map<String, Object>> rows,
        boolean pendingConfirmation,
        String operationId,
        Integer affectedRows,
        String errorMessage
) {
    public static ChatResponse answer(String text) {
        return new ChatResponse("answer", text, null, List.of(), List.of(), false, null, null, null);
    }

    public static ChatResponse withResults(String text, SqlPlan plan, List<String> cols, List<Map<String, Object>> rows) {
        return new ChatResponse("query_result", text, plan, safe(cols), safe(rows), false, null, null, null);
    }

    public static ChatResponse pendingConfirmation(String text, SqlPlan plan, String operationId) {
        return new ChatResponse("confirmation_required", text, plan, List.of(), List.of(), true, operationId, null, null);
    }

    public static ChatResponse executionResult(String text, SqlPlan plan, int affectedRows) {
        return new ChatResponse("execution_result", text, plan, List.of(), List.of(), false, null, affectedRows, null);
    }

    public static ChatResponse error(String errorMessage) {
        return new ChatResponse("error", null, null, List.of(), List.of(), false, null, null, errorMessage);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
