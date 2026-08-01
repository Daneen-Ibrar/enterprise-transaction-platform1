package com.enterprise.simulator;

public enum SimulatorToken {
    // Payment outcomes
    APPROVE("sim_approve"),
    DECLINE("sim_decline"),
    TIMEOUT("sim_timeout"),
    DELAY_5S("sim_delay_5s"),
    DELAY_10S("sim_delay_10s"),
    FAILURE("sim_failure"),
    DUPLICATE("sim_duplicate"),

    // Refund outcomes
    REFUND_APPROVE("sim_refund_approve"),
    REFUND_DECLINE("sim_refund_decline"),
    REFUND_FAILURE("sim_refund_failure"),
    REFUND_DELAY("sim_refund_delay");

    private final String token;

    SimulatorToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public static boolean isToken(String description) {
        if (description == null) return false;
        for (SimulatorToken token : values()) {
            if (description.toLowerCase().contains(token.getToken())) {
                return true;
            }
        }
        return false;
    }

    public static SimulatorToken fromDescription(String description) {
        if (description == null) return null;
        for (SimulatorToken token : values()) {
            if (description.toLowerCase().contains(token.getToken())) {
                return token;
            }
        }
        return null;
    }
}