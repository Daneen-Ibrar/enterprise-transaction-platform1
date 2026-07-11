package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Role;
import com.enterprise.identity.RoleRepository;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import com.enterprise.tenant.TenantRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;

    public AdminUserController(UserRepository userRepository,
                               RoleRepository roleRepository,
                               NotificationService notificationService,
                               PasswordEncoder passwordEncoder,
                               TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
    }

    // ----- List all users with search (bypass tenant filter for admin) -----
    @GetMapping
    public String listUsers(@RequestParam(required = false) String search,
                            @RequestParam(required = false) String role,
                            Model model) {
        // Get all users (admin sees everyone)
        List<AppUser> users = userRepository.findAllWithoutTenantFilter();

        // Filter by search
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getEmail().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }
        // Filter by role
        if (role != null && !role.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals(role)))
                    .collect(Collectors.toList());
        }

        // Set tenant name for each user
        for (AppUser user : users) {
            if (user.getTenantId() != null) {
                tenantRepository.findById(user.getTenantId())
                        .ifPresent(tenant -> user.setTenantName(tenant.getName()));
            } else {
                user.setTenantName("N/A");
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute("selectedRole", role);
        model.addAttribute("allRoles", roleRepository.findAll());
        return "admin/users/list";
    }

    // ----- Show create user form -----
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new AppUser());
        model.addAttribute("allRoles", roleRepository.findAll());
        model.addAttribute("allTenants", tenantRepository.findAll());
        return "admin/users/create";
    }

    // ----- Create new user (tenantId optional, falls back to 1) -----
    @PostMapping
    public String createUser(@RequestParam String email,
                             @RequestParam String password,
                             @RequestParam(required = false) List<Long> roleIds,
                             @RequestParam(required = false) Long tenantId,
                             RedirectAttributes redirectAttributes) {
        if (userRepository.findByEmail(email).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "User with this email already exists.");
            return "redirect:/admin/users/create";
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);

        if (tenantId == null) {
            tenantId = 1L;
        }
        user.setTenantId(tenantId);

        if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
            user.setRoles(roles);
        } else {
            Role defaultRole = roleRepository.findByName("CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Default role CUSTOMER not found"));
            user.getRoles().add(defaultRole);
        }

        userRepository.save(user);

        notificationService.createNotification(
            user.getId(),
            "ACCOUNT_CREATED",
            "Account Created",
            "An administrator has created your account. You can log in with your email and the password provided.",
            "/login"
        );

        redirectAttributes.addFlashAttribute("success", "User created successfully.");
        return "redirect:/admin/users";
    }

    // ----- Show edit user form (roles + status + tenant) -----
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("allRoles", roleRepository.findAll());
        model.addAttribute("allTenants", tenantRepository.findAll());
        model.addAttribute("userRoleIds", user.getRoles().stream().map(Role::getId).collect(Collectors.toList()));
        return "admin/users/edit";
    }

    // ----- Update user roles, active status, and tenant -----
    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id,
                             @RequestParam(required = false) List<Long> roleIds,
                             @RequestParam(required = false) Boolean active,
                             @RequestParam(required = false) Long tenantId,
                             RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (tenantId == null) {
            tenantId = user.getTenantId();
        }
        user.setTenantId(tenantId);

        if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
            user.setRoles(roles);
        } else {
            Role defaultRole = roleRepository.findByName("CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Default role CUSTOMER not found"));
            user.getRoles().clear();
            user.getRoles().add(defaultRole);
        }

        if (active != null) {
            user.setActive(active);
            if (!active) {
                notificationService.createNotification(
                    user.getId(),
                    "ACCOUNT_REVOKED",
                    "Account Disabled",
                    "Your account has been disabled by an administrator.",
                    "/login"
                );
            } else {
                notificationService.createNotification(
                    user.getId(),
                    "ACCOUNT_RESTORED",
                    "Account Reactivated",
                    "Your account has been reactivated by an administrator.",
                    "/login"
                );
            }
        }

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "User updated successfully.");
        return "redirect:/admin/users";
    }

    // ----- Revoke merchant by ID (used by suspicious page) -----
    @PostMapping("/revoke/{userId}")
    @ResponseBody
    public String revokeUserById(@PathVariable Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);

        notificationService.createNotification(
            user.getId(),
            "ACCOUNT_REVOKED",
            "Account Revoked",
            "Your merchant account has been revoked by an administrator.",
            "/login"
        );

        return "Merchant account revoked.";
    }

    // ----- Revoke customer by email (for backward compatibility) -----
    @PostMapping("/revoke")
    @ResponseBody
    public String revokeUserByEmail(@RequestParam String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);

        notificationService.createNotification(
            user.getId(),
            "ACCOUNT_REVOKED",
            "Account Revoked",
            "Your account has been revoked by an administrator.",
            "/login"
        );

        return "User access revoked.";
    }

    // ----- Restore page: list inactive users -----
    @GetMapping("/restore")
    public String restorePage(@RequestParam(required = false) String search, Model model) {
        List<AppUser> inactiveUsers;
        if (search != null && !search.isEmpty()) {
            inactiveUsers = userRepository.findAll().stream()
                    .filter(u -> !u.isActive() && u.getEmail().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        } else {
            inactiveUsers = userRepository.findAll().stream()
                    .filter(u -> !u.isActive())
                    .collect(Collectors.toList());
        }
        model.addAttribute("users", inactiveUsers);
        model.addAttribute("search", search);
        return "admin/users/restore";
    }

    // ----- Restore a user (set active = true) -----
    @PostMapping("/{id}/restore")
    public String restoreUser(@PathVariable Long id, @RequestParam(required = false) String search) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);

        notificationService.createNotification(
            user.getId(),
            "ACCOUNT_RESTORED",
            "Account Restored",
            "Your account has been reactivated by an administrator.",
            "/login"
        );

        return "redirect:/admin/users/restore" + (search != null ? "?search=" + search : "");
    }
}