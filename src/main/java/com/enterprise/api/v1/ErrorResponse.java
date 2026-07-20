package com.enterprise.api.v1;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {

    private final String code;
    private final String message;
    private final String correlationId;
    private final Instant timestamp;
    private final List<FieldError> fieldErrors;

    public ErrorResponse(String code, String message, String correlationId) {
        this(code, message, correlationId, List.of());
    }

    public ErrorResponse(String code, String message, String correlationId, List<FieldError> fieldErrors) {
        this.code = code;
        this.message = message;
        this.correlationId = correlationId;
        this.timestamp = Instant.now();
        this.fieldErrors = fieldErrors;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
    public Instant getTimestamp() { return timestamp; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    public static class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
