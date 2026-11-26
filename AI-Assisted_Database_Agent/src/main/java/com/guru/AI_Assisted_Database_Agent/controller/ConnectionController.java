package com.guru.AI_Assisted_Database_Agent.controller;

import com.guru.AI_Assisted_Database_Agent.model.ApiErrorResponse;
import com.guru.AI_Assisted_Database_Agent.model.ConnectionResponse;
import com.guru.AI_Assisted_Database_Agent.model.ConnectionTestResult;
import com.guru.AI_Assisted_Database_Agent.model.DbConnectionRequest;
import com.guru.AI_Assisted_Database_Agent.model.SchemaInfo;
import com.guru.AI_Assisted_Database_Agent.model.SchemaSnapshot;
import com.guru.AI_Assisted_Database_Agent.model.SessionState;
import com.guru.AI_Assisted_Database_Agent.service.ConnectionService;
import com.guru.AI_Assisted_Database_Agent.service.SchemaService;
import com.guru.AI_Assisted_Database_Agent.service.SessionStoreService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);

    private final ConnectionService connectionService;
    private final SchemaService schemaService;
    private final SessionStoreService sessionStore;

    public ConnectionController(
            ConnectionService connectionService,
            SchemaService schemaService,
            SessionStoreService sessionStore
    ) {
        this.connectionService = connectionService;
        this.schemaService = schemaService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/connections")
    public ResponseEntity<?> connect(@Valid @RequestBody DbConnectionRequest request) {
        ConnectionTestResult test = connectionService.testConnection(request);
        if (!test.success()) {
            log.info("Database connection test failed for type={}", request.dbType());
            return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                    "DATABASE_CONNECTION_FAILED",
                    test.message()
            ));
        }

        String sessionId = UUID.randomUUID().toString();
        SessionState state = sessionStore.getOrCreate(sessionId);
        try {
            connectionService.createSessionDataSource(request, state);
            SchemaSnapshot snapshot = schemaService.introspect(state.getDataSource(), request.schema());
            state.setSchema(snapshot.schema());
            state.setSchemaFilter(request.schema());
            state.setSchemaSummary(snapshot.promptSummary());

            return ResponseEntity.ok(new ConnectionResponse(
                    true,
                    sessionId,
                    test.databaseProductName(),
                    test.databaseProductVersion(),
                    "Connected successfully. Ask your database a question.",
                    snapshot.schema()
            ));
        } catch (RuntimeException exception) {
            sessionStore.remove(sessionId);
            throw exception;
        }
    }

    @GetMapping("/sessions/{sessionId}/schema")
    public ResponseEntity<?> schema(@PathVariable String sessionId) {
        SessionState state = sessionStore.get(sessionId);
        if (state == null || !state.isConnected()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of("SESSION_NOT_FOUND", "No active database session."));
        }
        SchemaSnapshot snapshot = schemaService.introspect(state.getDataSource(), state.getSchemaFilter());
        state.setSchema(snapshot.schema());
        state.setSchemaSummary(snapshot.promptSummary());
        SchemaInfo schema = snapshot.schema();
        return ResponseEntity.ok(Map.of("schema", schema));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable String sessionId) {
        boolean removed = sessionStore.remove(sessionId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", removed ? "Session closed." : "Session was already closed."
        ));
    }
}
