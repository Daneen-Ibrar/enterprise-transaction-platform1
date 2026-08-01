package com.enterprise.transaction;

public enum TransactionStatus {
    PENDING,
    AUTHORISED,
    SETTLED,
    FAILED,
    REVERSED,
    REFUNDED;

    public boolean canTransitionTo(TransactionStatus target) {
        return switch (this) {
            case PENDING -> target == AUTHORISED || target == FAILED;
            case AUTHORISED -> target == SETTLED || target == REVERSED;
            case SETTLED -> target == REFUNDED;
            case FAILED, REVERSED, REFUNDED -> false;
        };
    }
}