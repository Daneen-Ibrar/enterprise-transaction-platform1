package com.enterprise.exception;

public class DuplicateRequestConflictException extends RuntimeException {
    public DuplicateRequestConflictException(String message) {
        super(message);
    }
}