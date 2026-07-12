package com.enterprise.api;

import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTenantController {

    private final TenantRepository tenantRepository;

    public AdminTenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public String listTenants(Model model) {
        List<Tenant> tenants = tenantRepository.findAll();
        model.addAttribute("tenants", tenants);
        return "admin/tenants/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("tenant", new Tenant());
        return "admin/tenants/create";
    }
@PostMapping
public String createTenant(@ModelAttribute Tenant tenant, RedirectAttributes redirectAttributes) {
    try {
        tenant.setCreatedAt(LocalDateTime.now());
        tenantRepository.save(tenant);
        redirectAttributes.addFlashAttribute("success", "Tenant created.");
    } catch (DataIntegrityViolationException e) {
        if (e.getMessage().contains("duplicate key")) {
            redirectAttributes.addFlashAttribute("error", "Tenant with this name already exists or ID conflict.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to create tenant: " + e.getMessage());
        }
    }
    return "redirect:/admin/tenants";
}

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        model.addAttribute("tenant", tenant);
        return "admin/tenants/edit";
    }

    @PostMapping("/{id}")
    public String updateTenant(@PathVariable Long id, @ModelAttribute Tenant tenant, RedirectAttributes redirectAttributes) {
        tenant.setId(id);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantRepository.save(tenant);
        redirectAttributes.addFlashAttribute("success", "Tenant updated.");
        return "redirect:/admin/tenants";
    }

    @PostMapping("/{id}/delete")
    public String deleteTenant(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tenantRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Tenant deleted.");
        return "redirect:/admin/tenants";
    }

}