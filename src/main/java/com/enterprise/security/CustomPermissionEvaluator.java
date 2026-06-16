package com.enterprise.security;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Permission;
import com.enterprise.identity.Role;
import com.enterprise.identity.UserRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final UserRepository userRepository;

    public CustomPermissionEvaluator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permissionToken) {
        // For object-level permission, we can check if the user owns the object.
        // For simplicity, we'll only handle permission strings.
        return hasPermission(authentication, permissionToken.toString());
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permissionToken) {
        // targetId could be an invoice ID, etc. We can implement later.
        return hasPermission(authentication, permissionToken.toString());
    }

    private boolean hasPermission(Authentication authentication, String permissionString) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String username = authentication.getName();
        AppUser user = userRepository.findByEmail(username).orElse(null);
        if (user == null) {
            return false;
        }
        // Check if any of the user's roles have the given permission
        Set<Role> roles = user.getRoles();
        for (Role role : roles) {
            for (Permission perm : role.getPermissions()) {
                String token = perm.getResource() + ":" + perm.getAction();
                if (token.equals(permissionString)) {
                    return true;
                }
            }
        }
        return false;
    }
}