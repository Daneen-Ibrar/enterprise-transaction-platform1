package com.enterprise.util;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Role;
import com.enterprise.identity.RoleRepository;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class TestDataBuilder {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataBuilder(UserRepository userRepository,
                           RoleRepository roleRepository,
                           TenantRepository tenantRepository,
                           InvoiceRepository invoiceRepository,
                           InvoiceService invoiceService,
                           TransactionRepository transactionRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Tenant createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setDescription("Test tenant: " + name);
        tenant.setActive(true);
        return tenantRepository.save(tenant);
    }

    public AppUser createUser(String email, String roleName, Tenant tenant) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setTenantId(tenant.getId());
        user.setActive(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    public Invoice createInvoice(Long merchantId, String customerEmail, BigDecimal amount, String description) {
        return invoiceService.createInvoice(amount, description, customerEmail, merchantId, false, "GBP");
    }

    public Invoice createInvoiceApproved(Long merchantId, String customerEmail, BigDecimal amount, String description) {
        Invoice invoice = createInvoice(merchantId, customerEmail, amount, description);
        invoice.setStatus("APPROVED");
        return invoiceRepository.save(invoice);
    }

    public void cleanup() {
        transactionRepository.deleteAll();
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }
}