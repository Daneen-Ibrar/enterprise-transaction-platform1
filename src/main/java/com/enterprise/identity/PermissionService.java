package com.enterprise.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public boolean hasPermission(String resource, String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        // The user's roles are loaded with permissions in the UserDetailsService
        // We can extract from the principal if it's a custom UserDetails
        // For simplicity, we fetch from DB each time (cache later)
        // But a better approach: load permissions into the Authentication object.
        // We'll implement a custom UserDetails that includes permissions.
        return false; // Placeholder – we'll implement via PermissionEvaluator
    }
}