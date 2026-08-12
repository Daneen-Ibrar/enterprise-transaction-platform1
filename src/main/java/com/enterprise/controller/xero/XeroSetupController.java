package com.enterprise.controller.xero;

import com.enterprise.tenant.TenantContext;
import com.enterprise.xero.entity.XeroToken;
import com.enterprise.xero.service.XeroOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/xero/setup")
public class XeroSetupController {

    private static final Logger log = LoggerFactory.getLogger(XeroSetupController.class);

    private final XeroOAuthService xeroOAuthService;

    public XeroSetupController(XeroOAuthService xeroOAuthService) {
        this.xeroOAuthService = xeroOAuthService;
    }

    @GetMapping
    public String setupPage(Model model) {
        Long tenantId = TenantContext.getRequiredTenantId();
        boolean isConnected = xeroOAuthService.isConnected(tenantId);
        boolean hasCustomCreds = xeroOAuthService.hasCustomCredentials(tenantId);

        model.addAttribute("tenantId", tenantId);
        model.addAttribute("isConnected", isConnected);
        model.addAttribute("hasCustomCreds", hasCustomCreds);
        model.addAttribute("usesDefaultCredentials", !hasCustomCreds && !isConnected);

        return "admin/xero/setup";
    }

    @PostMapping("/connect")
    public String connectWithCredentials(@RequestParam String clientId,
                                         @RequestParam String clientSecret,
                                         RedirectAttributes redirectAttributes) {
        Long tenantId = TenantContext.getRequiredTenantId();

        try {
            log.info("🔑 Tenant {} initiating Xero connection with custom credentials", tenantId);
            // TODO: Store credentials before redirecting to Xero
            String authUrl = xeroOAuthService.getAuthorizationUrlForTenant(tenantId);
            return "redirect:" + authUrl;
        } catch (Exception e) {
            log.error("Failed to connect to Xero", e);
            redirectAttributes.addFlashAttribute("error", "Failed to connect: " + e.getMessage());
            return "redirect:/admin/xero/setup";
        }
    }

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error,
                           RedirectAttributes redirectAttributes) {
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", "Xero authorization failed: " + error);
            return "redirect:/admin/xero/setup";
        }

        Long tenantId = TenantContext.getRequiredTenantId();

        try {
            XeroToken token = xeroOAuthService.exchangeCodeForTokens(tenantId, code, state);
            redirectAttributes.addFlashAttribute("success", "✅ Successfully connected to Xero!");
            log.info("✅ Xero connected for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to connect to Xero", e);
            redirectAttributes.addFlashAttribute("error", "Failed to connect to Xero: " + e.getMessage());
        }

        return "redirect:/admin/xero";
    }

    @PostMapping("/disconnect")
    public String disconnect(RedirectAttributes redirectAttributes) {
        Long tenantId = TenantContext.getRequiredTenantId();
        try {
            xeroOAuthService.disconnect(tenantId);
            redirectAttributes.addFlashAttribute("success", "✅ Disconnected from Xero");
        } catch (Exception e) {
            log.error("Failed to disconnect", e);
            redirectAttributes.addFlashAttribute("error", "Failed to disconnect: " + e.getMessage());
        }
        return "redirect:/admin/xero/setup";
    }
}