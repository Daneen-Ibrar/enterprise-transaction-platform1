package com.enterprise.reliability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class ReliabilityAspect {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityAspect.class);
    private final RecoveryService recoveryService;

    public ReliabilityAspect(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    // Intercept methods annotated with @Reliable
    @Around("@annotation(com.enterprise.reliability.Reliable)")
    public Object applyReliability(ProceedingJoinPoint joinPoint) throws Throwable {
        // Skip if the method is inside the reliability package to avoid recursion
        String packageName = joinPoint.getSignature().getDeclaringType().getPackage().getName();
        if (packageName.startsWith("com.enterprise.reliability")) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String operationType = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        Map<String, Object> context = new HashMap<>();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            context.put(paramNames[i], args[i]);
        }

        try {
            return recoveryService.executeWithRetry(operationType, () -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }, context);
        } catch (Exception e) {
            log.error("Operation {} failed after retries: {}", operationType, e.getMessage());
            throw e;
        }
    }
}