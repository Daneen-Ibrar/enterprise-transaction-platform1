package com.enterprise.tenant;

public class TenantContext {
    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        if (tenantId != null) {
            currentTenantId.set(tenantId);
        } else {
            currentTenantId.remove();
        }
    }

    public static Long getTenantId() {
        return currentTenantId.get();
    }

    public static Long getRequiredTenantId() {
        Long tenantId = currentTenantId.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant ID found in context. Ensure TenantInterceptor is configured.");
        }
        return tenantId;
    }

    public static boolean hasTenantId() {
        return currentTenantId.get() != null;
    }

    public static void clear() {
        currentTenantId.remove();
    }
}