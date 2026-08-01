package com.enterprise.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
    private final String code;
    private final String message;
    private final String correlationId;
    private final Instant timestamp;
    private final List<FieldError> fieldErrors;

    public ErrorResponse(String code, String message, String correlationId,
                         Instant timestamp, List<FieldError> fieldErrors) {
        this.code = code;
        this.message = message;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.fieldErrors = fieldErrors;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
    public Instant getTimestamp() { return timestamp; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }
}