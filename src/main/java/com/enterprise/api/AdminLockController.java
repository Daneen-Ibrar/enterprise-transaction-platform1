package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.security.LoginAttemptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/locks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLockController {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public AdminLockController(UserRepository userRepository,
                               LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping
    public String listLockedUsers(Model model) {
        List<AppUser> lockedUsers = userRepository.findAll().stream()
                .filter(u -> u.isAccountLocked() && (u.getLockExpiry() == null ||
                        u.getLockExpiry().isAfter(LocalDateTime.now())))
                .collect(Collectors.toList());
        model.addAttribute("lockedUsers", lockedUsers);
        return "admin/locks/list";
    }

    @PostMapping("/unlock")
    public String unlockUser(@RequestParam Long userId,
                             RedirectAttributes redirectAttributes) {
        loginAttemptService.unlockAccount(userId);
        redirectAttributes.addFlashAttribute("success", "User unlocked successfully.");
        return "redirect:/admin/locks";
    }
}