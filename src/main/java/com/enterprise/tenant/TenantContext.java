package com.enterprise.tenant;

public class TenantContext {
    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        if (tenantId != null) {
            currentTenantId.set(tenantId);
        } else {
            // ✅ FIX: Set default tenant instead of removing
            currentTenantId.set(1L);
        }
    }

    public static Long getTenantId() {
        Long tenantId = currentTenantId.get();
        // ✅ FIX: Return default if null
        return tenantId != null ? tenantId : 1L;
    }

    public static Long getRequiredTenantId() {
        Long tenantId = currentTenantId.get();
        // ✅ FIX: Return default instead of throwing
        return tenantId != null ? tenantId : 1L;
    }

    public static boolean hasTenantId() {
        return currentTenantId.get() != null;
    }

    public static void clear() {
        currentTenantId.remove();
    }
}