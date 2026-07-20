package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.security.BackupCodeService;
import com.enterprise.security.TOTPService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/2fa")
public class TwoFactorController {

    private final TOTPService totpService;
    private final UserRepository userRepository;
    private final BackupCodeService backupCodeService;

    public TwoFactorController(TOTPService totpService,
                               UserRepository userRepository,
                               BackupCodeService backupCodeService) {
        this.totpService = totpService;
        this.userRepository = userRepository;
        this.backupCodeService = backupCodeService;
    }

    @GetMapping("/setup")
    public String setup(Authentication authentication, Model model) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTwoFactorEnabled()) {
            model.addAttribute("enabled", true);
            model.addAttribute("backupCodesCount", backupCodeService.getRemainingCodeCount(user.getId()));
            return "twofactor/status";
        }

        String secretKey = totpService.generateSecretKey();
        String otpAuthUrl = totpService.getOtpAuthUrl(secretKey, user.getEmail());

        model.addAttribute("secretKey", secretKey);
        model.addAttribute("otpAuthUrl", otpAuthUrl);
        return "twofactor/setup";
    }

    @PostMapping("/enable")
    public String enable(@RequestParam String secretKey,
                         @RequestParam int code,
                         Authentication authentication,
                         Model model) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (totpService.verifyCode(secretKey, code)) {
            user.setSecretKey(secretKey);
            user.setTwoFactorEnabled(true);
            userRepository.save(user);

            // Generate backup codes
            List<String> backupCodes = backupCodeService.generateBackupCodes(user.getId());

            model.addAttribute("enabled", true);
            model.addAttribute("backupCodes", backupCodes);
            return "twofactor/status";
        } else {
            model.addAttribute("error", "Invalid code. Please try again.");
            model.addAttribute("secretKey", secretKey);
            String otpAuthUrl = totpService.getOtpAuthUrl(secretKey, user.getEmail());
            model.addAttribute("otpAuthUrl", otpAuthUrl);
            return "twofactor/setup";
        }
    }

    @PostMapping("/disable")
    public String disable(Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(false);
        user.setSecretKey(null);
        userRepository.save(user);

        // Delete backup codes
        backupCodeService.regenerateCodes(user.getId()); // This deletes them

        return "redirect:/2fa/setup?disabled";
    }

    @GetMapping("/verify")
    public String verifyForm() {
        return "twofactor/verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam(required = false) Integer code,
                         @RequestParam(required = false) String backupCode,
                         Authentication authentication,
                         HttpSession session,
                         Model model) {
        try {
            AppUser user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.isTwoFactorEnabled()) {
                boolean verified = false;

                // Check TOTP code
                if (code != null) {
                    verified = totpService.verifyCode(user.getSecretKey(), code);
                    if (verified) {
                        session.setAttribute("2FA_AUTHENTICATED", true);
                        session.removeAttribute("2FA_PENDING");
                        return "redirect:/dashboard";
                    }
                }

                // Check backup code
                if (backupCode != null && !backupCode.isEmpty()) {
                    verified = backupCodeService.verifyBackupCode(user.getId(), backupCode);
                    if (verified) {
                        session.setAttribute("2FA_AUTHENTICATED", true);
                        session.removeAttribute("2FA_PENDING");
                        return "redirect:/dashboard";
                    }
                }

                model.addAttribute("error", "Invalid code. Please try again.");
                return "twofactor/verify";
            } else {
                return "redirect:/dashboard";
            }
        } catch (Exception e) {
            System.err.println("ERROR in 2FA verification: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Internal error: " + e.getMessage());
            return "twofactor/verify";
        }
    }

    @GetMapping("/backup-codes")
    public String viewBackupCodes(Authentication authentication, Model model) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            return "redirect:/2fa/setup";
        }

        // Show unused codes (but we only have hashes, so show count)
        long remaining = backupCodeService.getRemainingCodeCount(user.getId());
        model.addAttribute("remainingCount", remaining);
        model.addAttribute("hasCodes", remaining > 0);
        return "twofactor/backup-codes";
    }

    @PostMapping("/backup-codes/regenerate")
    public String regenerateBackupCodes(Authentication authentication, Model model) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            return "redirect:/2fa/setup";
        }

        List<String> newCodes = backupCodeService.generateBackupCodes(user.getId());
        model.addAttribute("backupCodes", newCodes);
        model.addAttribute("regenerated", true);
        return "twofactor/status";
    }
}