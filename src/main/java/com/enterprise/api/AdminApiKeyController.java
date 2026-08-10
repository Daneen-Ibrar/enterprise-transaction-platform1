package com.enterprise.api;

import com.enterprise.apikey.ApiKey;
import com.enterprise.apikey.ApiKeyRepository; // 👈 ADD THIS IMPORT
import com.enterprise.apikey.ApiKeyResponse;
import com.enterprise.apikey.ApiKeyService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/api-keys")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository; // 👈 ADD THIS
    private final UserRepository userRepository;

    public AdminApiKeyController(ApiKeyService apiKeyService,
                                 ApiKeyRepository apiKeyRepository, // 👈 ADD THIS
                                 UserRepository userRepository) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository; // 👈 ADD THIS
        this.userRepository = userRepository;
    }

    private AppUser getCurrentAdmin(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // View page (HTML)
    @GetMapping
    public String listKeysPage(Model model) {
        return "admin/api-keys";
    }

    // REST endpoint for listing with filters
    @GetMapping("/list")
    @ResponseBody
    public List<ApiKeyResponse> listKeys(@RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) String search,
                                         Authentication authentication) {
        AppUser admin = getCurrentAdmin(authentication);
        Long tenantId = admin.getTenantId();

        List<ApiKey> allKeys = apiKeyRepository.findAllByTenantId(tenantId); // ✅ now works

        // Filter by active status
        if (active != null) {
            allKeys = allKeys.stream()
                    .filter(k -> k.isActive() == active)
                    .collect(Collectors.toList());
        }

        // Filter by search term
        if (search != null && !search.trim().isEmpty()) {
            String term = search.trim().toLowerCase();
            allKeys = allKeys.stream()
                    .filter(k -> k.getKeyValue().toLowerCase().contains(term) ||
                            k.getName().toLowerCase().contains(term) ||
                            k.getUser().getEmail().toLowerCase().contains(term))
                    .collect(Collectors.toList());
        }

        return allKeys.stream()
                .map(ApiKeyResponse::new)
                .collect(Collectors.toList());
    }

    @PostMapping("/generate")
    @ResponseBody
    public ApiKeyResponse generateKey(@RequestParam String name, Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        ApiKey key = apiKeyService.generateApiKey(admin, name);
        return new ApiKeyResponse(key);
    }

    @PostMapping("/{id}/revoke")
    @ResponseBody
    public String revokeKey(@PathVariable Long id) {
        apiKeyService.revokeApiKey(id);
        return "API Key revoked.";
    }
}