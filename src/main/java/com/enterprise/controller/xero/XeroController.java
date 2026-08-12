package com.enterprise.controller.xero;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.TenantContext;
import com.enterprise.xero.entity.XeroToken;
import com.enterprise.xero.service.XeroInvoiceService;
import com.enterprise.xero.service.XeroOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/xero")
public class XeroController {

    private static final Logger log = LoggerFactory.getLogger(XeroController.class);

    private final XeroOAuthService xeroOAuthService;
    private final XeroInvoiceService xeroInvoiceService;
    private final InvoiceService invoiceService;
    private final FeatureFlagService featureFlagService;  // ✅ ADD THIS

    public XeroController(XeroOAuthService xeroOAuthService,
                          XeroInvoiceService xeroInvoiceService,
                          InvoiceService invoiceService,
                          FeatureFlagService featureFlagService) {  // ✅ ADD TO CONSTRUCTOR
        this.xeroOAuthService = xeroOAuthService;
        this.xeroInvoiceService = xeroInvoiceService;
        this.invoiceService = invoiceService;
        this.featureFlagService = featureFlagService;  // ✅ ADD THIS
    }

    @GetMapping
    public String xeroDashboard(Model model) {
        Long tenantId = TenantContext.getRequiredTenantId();
        boolean isConnected = xeroOAuthService.isConnected(tenantId);

        // ✅ Check if auto-sync is enabled
        boolean autoSyncEnabled = featureFlagService.isEnabled("XERO_AUTO_SYNC");

        model.addAttribute("isConnected", isConnected);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("autoSyncEnabled", autoSyncEnabled);

        if (!isConnected) {
            String authUrl = xeroOAuthService.getAuthorizationUrlForTenant(tenantId);
            model.addAttribute("authUrl", authUrl);
        }

        return "admin/xero/dashboard";
    }

    @GetMapping("/connect")
    public String connectXero() {
        Long tenantId = TenantContext.getRequiredTenantId();
        String authUrl = xeroOAuthService.getAuthorizationUrlForTenant(tenantId);
        return "redirect:" + authUrl;
    }

    @GetMapping("/callback")
    public String xeroCallback(@RequestParam("code") String code,
                               @RequestParam(value = "state", required = false) String state,
                               @RequestParam(value = "error", required = false) String error,
                               RedirectAttributes redirectAttributes) {
        log.info("✅ Xero callback received with code: {}", 
                code != null ? code.substring(0, Math.min(code.length(), 10)) + "..." : "null");

        if (error != null) {
            log.error("Xero authorization error: {}", error);
            redirectAttributes.addFlashAttribute("error", "Xero authorization failed: " + error);
            return "redirect:/admin/xero";
        }

        try {
            Long tenantId = TenantContext.getRequiredTenantId();
            XeroToken token = xeroOAuthService.exchangeCodeForTokens(tenantId, code, state);
            redirectAttributes.addFlashAttribute("success", "✅ Successfully connected to Xero!");
            log.info("✅ Xero connected for tenant: {}", token.getTenantId());
        } catch (Exception e) {
            log.error("Failed to connect to Xero", e);
            redirectAttributes.addFlashAttribute("error", "Failed to connect to Xero: " + e.getMessage());
        }

        return "redirect:/admin/xero";
    }

    @PostMapping("/disconnect")
    public String disconnectXero(RedirectAttributes redirectAttributes) {
        Long tenantId = TenantContext.getRequiredTenantId();
        try {
            xeroOAuthService.disconnect(tenantId);
            redirectAttributes.addFlashAttribute("success", "✅ Disconnected from Xero");
            log.info("✅ Xero disconnected for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to disconnect Xero", e);
            redirectAttributes.addFlashAttribute("error", "Failed to disconnect: " + e.getMessage());
        }
        return "redirect:/admin/xero";
    }

    @PostMapping("/sync")
    public String syncInvoices(RedirectAttributes redirectAttributes) {
        Long tenantId = TenantContext.getRequiredTenantId();

        try {
            if (!xeroOAuthService.isConnected(tenantId)) {
                redirectAttributes.addFlashAttribute("error", "Please connect to Xero first");
                return "redirect:/admin/xero";
            }

            // ✅ Only sync invoices that haven't been synced yet
            List<Invoice> invoices = invoiceService.findAll().stream()
                    .filter(i -> "APPROVED".equals(i.getStatus()) || "PAID".equals(i.getStatus()))
                    .filter(i -> i.getXeroInvoiceId() == null || i.getXeroInvoiceId().isEmpty())
                    .limit(10)
                    .collect(Collectors.toList());

            log.info("🔍 Found {} invoices to sync (not yet synced to Xero)", invoices.size());

            if (invoices.isEmpty()) {
                redirectAttributes.addFlashAttribute("info", "No invoices to sync to Xero (all already synced or none approved)");
                return "redirect:/admin/xero";
            }

            int synced = 0;
            int failed = 0;

            for (Invoice invoice : invoices) {
                try {
                    String xeroId = xeroInvoiceService.syncInvoiceToXero(invoice);
                    if (xeroId != null) {
                        synced++;
                        log.info("✅ Synced invoice {} to Xero with ID: {}", invoice.getId(), xeroId);
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to sync invoice {} to Xero: {}", invoice.getId(), e.getMessage());
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    "✅ Synced " + synced + " invoices to Xero" + (failed > 0 ? " (" + failed + " failed)" : ""));

        } catch (Exception e) {
            log.error("Failed to sync invoices to Xero", e);
            redirectAttributes.addFlashAttribute("error", "Failed to sync: " + e.getMessage());
        }

        return "redirect:/admin/xero";
    }

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Long tenantId = TenantContext.getRequiredTenantId();
        boolean isConnected = xeroOAuthService.isConnected(tenantId);

        return Map.of(
                "connected", isConnected,
                "tenantId", tenantId,
                "hasValidToken", isConnected && xeroOAuthService.isConnected(tenantId)
        );
    }

    @GetMapping("/test-connection")
    @ResponseBody
    public Map<String, Object> testConnection() {
        Long tenantId = TenantContext.getRequiredTenantId();
        return Map.of(
                "tenantId", tenantId,
                "isConnected", xeroOAuthService.isConnected(tenantId)
        );
    }
}