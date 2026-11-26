package com.guru.AI_Assisted_Database_Agent.controller;

import com.guru.AI_Assisted_Database_Agent.model.ChatRequest;
import com.guru.AI_Assisted_Database_Agent.model.ChatResponse;
import com.guru.AI_Assisted_Database_Agent.model.ConfirmRequest;
import com.guru.AI_Assisted_Database_Agent.model.PendingOperation;
import com.guru.AI_Assisted_Database_Agent.model.SessionState;
import com.guru.AI_Assisted_Database_Agent.model.SqlPlan;
import com.guru.AI_Assisted_Database_Agent.service.AiSqlPlannerService;
import com.guru.AI_Assisted_Database_Agent.service.SessionStoreService;
import com.guru.AI_Assisted_Database_Agent.service.SqlExecutionService;
import com.guru.AI_Assisted_Database_Agent.service.SqlValidationService;
import com.guru.AI_Assisted_Database_Agent.service.PromptInjectionGuard;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AiSqlPlannerService plannerService;
    private final SqlValidationService validationService;
    private final SqlExecutionService executionService;
    private final SessionStoreService sessionStore;
    private final Duration operationTtl;

    public ChatController(
            AiSqlPlannerService plannerService,
            SqlValidationService validationService,
            SqlExecutionService executionService,
            SessionStoreService sessionStore,
            @Value("${app.operation.ttl:PT10M}") Duration operationTtl
    ) {
        this.plannerService = plannerService;
        this.validationService = validationService;
        this.executionService = executionService;
        this.sessionStore = sessionStore;
        if (operationTtl.isZero() || operationTtl.isNegative()) {
            throw new IllegalArgumentException("Operation TTL must be positive.");
        }
        this.operationTtl = operationTtl;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        if (PromptInjectionGuard.isSuspicious(request.userMessage())) {
            return ResponseEntity.ok(ChatResponse.answer("The request contains an instruction override or secret-disclosure pattern. Rephrase it as a database question."));
        }
        SessionState state = activeSession(request.sessionId());
        if (state == null) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("No active database session. Please connect first."));
        }
        synchronized (state) {
            PendingOperation pending = state.getPendingOperation();
            if (pending != null && !isExpired(pending)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ChatResponse.error("Confirm or cancel the pending operation before sending another request."));
            }
            if (pending != null) state.setPendingOperation(null);
        }

        try {
            SqlPlan plan = plannerService.plan(request.userMessage(), state, false);
            log.info("AI SQL plan received with intent={}", plan == null ? "null" : plan.intent());

            SqlValidationService.ValidationResult validation = validationService.validate(plan, state.getDbType(), false, state.getSchema());
            if (!validation.valid()) {
                state.addHistory("user", request.userMessage());
                state.addHistory("assistant", "Blocked by SQL policy.");
                return ResponseEntity.ok(ChatResponse.answer(validation.reason()));
            }

            if (validationService.requiresConfirmation(plan)) {
                PendingOperation operation = new PendingOperation(UUID.randomUUID().toString(), plan, Instant.now());
                synchronized (state) {
                    PendingOperation current = state.getPendingOperation();
                    if (current != null && !isExpired(current)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ChatResponse.error("Another operation is already awaiting confirmation."));
                    }
                    state.setPendingOperation(operation);
                }
                state.addHistory("user", request.userMessage());
                state.addHistory("assistant", "A " + plan.intent() + " operation is awaiting confirmation.");

                String answer = "Review the generated " + plan.intent()
                        + " statement and confirm it before execution. Reason: " + plan.reason();
                return ResponseEntity.ok(ChatResponse.pendingConfirmation(answer, plan, operation.operationId()));
            }

            SqlExecutionService.ExecutionResult result = executionService.execute(plan, state.getDataSource());
            String answer = buildReadAnswer(plan, result);
            state.addHistory("user", request.userMessage());
            state.addHistory("assistant", answer);
            return ResponseEntity.ok(ChatResponse.withResults(answer, plan, result.columns(), result.rows()));
        } catch (Exception exception) {
            log.error("Chat workflow failed: {}", exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ChatResponse.error("The AI request could not be completed safely. Please try again."));
        }
    }

    @PostMapping("/operations/confirm")
    public ResponseEntity<ChatResponse> confirmSql(@Valid @RequestBody ConfirmRequest request) {
        SessionState state = activeSession(request.sessionId());
        if (state == null) {
            return ResponseEntity.badRequest().body(ChatResponse.error("No active database session."));
        }

        PendingOperation operation;
        synchronized (state) {
            operation = state.getPendingOperation();
            if (operation == null || !operation.operationId().equals(request.operationId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ChatResponse.error("The pending operation does not exist or has already been handled."));
            }
            state.setPendingOperation(null);
        }

        if (isExpired(operation)) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ChatResponse.error("The pending operation expired. Generate it again before confirming."));
        }
        if (!request.confirm()) {
            state.addHistory("assistant", "Operation cancelled by user.");
            return ResponseEntity.ok(ChatResponse.answer("Operation cancelled. No changes were made."));
        }

        try {
            SqlValidationService.ValidationResult validation = validationService.validate(
                    operation.plan(), state.getDbType(), false, state.getSchema());
            if (!validation.valid()) return ResponseEntity.ok(ChatResponse.answer(validation.reason()));
            SqlExecutionService.ExecutionResult result = executionService.execute(operation.plan(), state.getDataSource());
            String answer = result.message();
            state.addHistory("assistant", answer);
            return ResponseEntity.ok(ChatResponse.executionResult(answer, operation.plan(), result.affectedRows()));
        } catch (Exception exception) {
            log.error("Confirmed database operation failed: {}", exception.getClass().getSimpleName());
            return ResponseEntity.unprocessableEntity()
                    .body(ChatResponse.error("The database rejected the confirmed operation. No successful result was recorded."));
        }
    }

    @DeleteMapping("/sessions/{sessionId}/history")
    public ResponseEntity<ChatResponse> clearHistory(@PathVariable String sessionId) {
        SessionState state = activeSession(sessionId);
        if (state == null) {
            return ResponseEntity.badRequest().body(ChatResponse.error("No active database session."));
        }
        synchronized (state) {
            state.clearHistory();
            state.setPendingOperation(null);
        }
        return ResponseEntity.ok(ChatResponse.answer("Conversation history cleared."));
    }

    private SessionState activeSession(String sessionId) {
        SessionState state = sessionStore.get(sessionId);
        return state != null && state.isConnected() ? state : null;
    }

    private String buildReadAnswer(SqlPlan plan, SqlExecutionService.ExecutionResult result) {
        if (result.rows().isEmpty()) return "The query returned no results.";
        String reason = plan.reason() == null || plan.reason().isBlank() ? "" : " " + plan.reason();
        return result.message() + reason;
    }

    private boolean isExpired(PendingOperation operation) {
        return operation.createdAt().isBefore(Instant.now().minus(operationTtl));
    }
}
