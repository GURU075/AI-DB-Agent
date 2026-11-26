package com.guru.AI_Assisted_Database_Agent.controller;

import com.guru.AI_Assisted_Database_Agent.model.ApiErrorResponse;
import com.guru.AI_Assisted_Database_Agent.service.ConnectionPolicyService;
import com.guru.AI_Assisted_Database_Agent.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("MALFORMED_REQUEST", "Request body is invalid."));
    }

    @ExceptionHandler(ConnectionPolicyService.ConnectionPolicyException.class)
    public ResponseEntity<ApiErrorResponse> connectionPolicy(ConnectionPolicyService.ConnectionPolicyException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("CONNECTION_TARGET_BLOCKED", exception.getMessage()));
    }

    @ExceptionHandler(SchemaService.SchemaIntrospectionException.class)
    public ResponseEntity<ApiErrorResponse> schemaFailure() {
        return ResponseEntity.unprocessableEntity()
                .body(ApiErrorResponse.of("SCHEMA_INTROSPECTION_FAILED", "Unable to inspect the selected schema."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        log.error("Unhandled API error: {}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "The request could not be completed."));
    }
}
