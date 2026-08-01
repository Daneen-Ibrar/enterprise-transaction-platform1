package com.enterprise.security;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Permission;
import com.enterprise.identity.Role;
import com.enterprise.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CustomPermissionEvaluator.class);
    private final UserRepository userRepository;

    public CustomPermissionEvaluator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permissionToken) {
        log.debug("Checking permission: {} for user: {}", permissionToken, authentication.getName());
        return hasPermission(authentication, permissionToken.toString());
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permissionToken) {
        log.debug("Checking permission: {} for user: {} on target: {}", permissionToken, authentication.getName(), targetId);
        return hasPermission(authentication, permissionToken.toString());
    }

    private boolean hasPermission(Authentication authentication, String permissionString) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authentication or not authenticated");
            return false;
        }
        String username = authentication.getName();
        AppUser user = userRepository.findByEmail(username).orElse(null);
        if (user == null) {
            log.warn("User not found: {}", username);
            return false;
        }
        Set<Role> roles = user.getRoles();
        for (Role role : roles) {
            for (Permission perm : role.getPermissions()) {
                String token = perm.getResource() + ":" + perm.getAction();
                if (token.equals(permissionString)) {
                    log.debug("Permission {} granted for user {}", permissionString, username);
                    return true;
                }
            }
        }
        log.warn("Permission {} denied for user {}", permissionString, username);
        return false;
    }
}