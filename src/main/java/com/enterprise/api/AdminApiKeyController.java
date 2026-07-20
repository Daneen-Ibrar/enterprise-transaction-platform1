package com.enterprise.api;

import com.enterprise.apikey.ApiKey;
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
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;

    public AdminApiKeyController(ApiKeyService apiKeyService,
                                 UserRepository userRepository) {
        this.apiKeyService = apiKeyService;
        this.userRepository = userRepository;
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
                                         @RequestParam(required = false) String search) {
        List<ApiKey> allKeys = apiKeyService.getAllKeys();

        // Filter by active status
        if (active != null) {
            allKeys = allKeys.stream()
                    .filter(k -> k.isActive() == active)
                    .collect(Collectors.toList());
        }

        // Filter by search term (case-insensitive, keyValue or name)
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
        
        // The key is saved with the full keyValue (not truncated)
        // So ApiKeyResponse will contain the full key
        return new ApiKeyResponse(key);
    }

    @PostMapping("/{id}/revoke")
    @ResponseBody
    public String revokeKey(@PathVariable Long id) {
        apiKeyService.revokeApiKey(id);
        return "API Key revoked.";
    }
}