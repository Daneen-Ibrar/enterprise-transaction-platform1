package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.security.TOTPService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/2fa")
public class TwoFactorController {

    private final TOTPService totpService;
    private final UserRepository userRepository;

    public TwoFactorController(TOTPService totpService, UserRepository userRepository) {
        this.totpService = totpService;
        this.userRepository = userRepository;
    }

    @GetMapping("/setup")
    public String setup(Authentication authentication, Model model) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTwoFactorEnabled()) {
            model.addAttribute("enabled", true);
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
            return "redirect:/2fa/setup?enabled";
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
        return "redirect:/2fa/setup?disabled";
    }

    @GetMapping("/verify")
    public String verifyForm() {
        return "twofactor/verify";
    }
@PostMapping("/verify")
public String verify(@RequestParam int code,
                     Authentication authentication,
                     HttpSession session,
                     Model model) {
    System.out.println(">>> verify POST called, code: " + code + ", user: " + authentication.getName());
    try {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTwoFactorEnabled() && totpService.verifyCode(user.getSecretKey(), code)) {
            session.setAttribute("2FA_AUTHENTICATED", true);
            session.removeAttribute("2FA_PENDING");
            return "redirect:/dashboard";
        } else {
            model.addAttribute("error", "Invalid code. Please try again.");
            return "twofactor/verify";
        }
    } catch (Exception e) {
        System.err.println("ERROR in 2FA verification: " + e.getMessage());
        e.printStackTrace();
        model.addAttribute("error", "Internal error: " + e.getMessage());
        return "twofactor/verify";
    }
}
}