package com.enterprise.simulator;

import com.enterprise.feature.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

    private final FeatureFlagService featureFlagService;

    public SimulatorService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public SimulatorResult simulate(String description) {
        // ✅ FIX: Disable simulator unless feature flag is enabled
        if (!featureFlagService.isEnabled("SIMULATOR_ENABLED")) {
            return null;
        }

        if (description == null) {
            return null;
        }
        SimulatorToken token = SimulatorToken.fromDescription(description);
        if (token == null) {
            return null;
        }
        log.info("Simulator token detected: {}", token);
        return switch (token) {
            case APPROVE -> new SimulatorResult("SETTLED", "Simulated approval");
            case DECLINE -> new SimulatorResult("FAILED", "Simulated decline");
            case TIMEOUT -> throw new RuntimeException("Simulated timeout");
            case FAILURE -> throw new RuntimeException("Simulated failure");
            case DUPLICATE -> new SimulatorResult("DUPLICATE", "Simulated duplicate");
            default -> null;
        };
    }

    public record SimulatorResult(String status, String message) {}
}