package com.enterprise.identity;

import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;

    public TestUserInitializer(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Set a default tenant for system initialization
        TenantContext.setTenantId(1L);
        try {
            createTestUser("TEST_CUSTOMER_EMAIL", "TEST_CUSTOMER_PASSWORD", "CUSTOMER");
            createTestUser("TEST_MERCHANT_EMAIL", "TEST_MERCHANT_PASSWORD", "MERCHANT");
            createTestUser("TEST_ADMIN_EMAIL", "TEST_ADMIN_PASSWORD", "ADMIN");
            createTestUser("TEST_AUDITOR_EMAIL", "TEST_AUDITOR_PASSWORD", "AUDITOR");
        } finally {
            TenantContext.clear();
        }
    }

    private void createTestUser(String emailEnv, String passwordEnv, String roleName) {
        String email = System.getenv(emailEnv);
        String password = System.getenv(passwordEnv);
        if (email == null || password == null) {
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        AppUser user = userRepository.findByEmail(email).orElse(new AppUser());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.getRoles().add(role);
        user.setTenantId(1L);
        user.setActive(true);
        // Mark admin@test.com as super admin
        if ("ADMIN".equals(roleName) && "admin@test.com".equals(email)) {
            user.setSuperAdmin(true);
        }
        userRepository.save(user);
    }
}