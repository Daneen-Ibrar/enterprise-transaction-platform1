package com.enterprise.api;

import com.enterprise.feature.FeatureFlag;
import com.enterprise.feature.FeatureFlagService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/features")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public String listFeatures(Model model) {
        List<FeatureFlag> flags = featureFlagService.findAll();
        model.addAttribute("flags", flags);
        return "admin/features/list";
    }

    @PostMapping("/{id}/toggle")
    public String toggleFeature(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        FeatureFlag flag = featureFlagService.findAll().stream()
                .filter(f -> f.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Feature not found"));
        featureFlagService.setEnabled(flag.getName(), !flag.isEnabled());
        redirectAttributes.addFlashAttribute("success", "Feature toggled successfully.");
        return "redirect:/admin/features";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("flag", new FeatureFlag());
        return "admin/features/create";
    }

    @PostMapping
    public String createFeature(@RequestParam String name,
                                @RequestParam String description,
                                @RequestParam(required = false) Boolean enabled,
                                RedirectAttributes redirectAttributes) {
        featureFlagService.createFlag(name, description, enabled != null && enabled);
        redirectAttributes.addFlashAttribute("success", "Feature created.");
        return "redirect:/admin/features";
    }

    @PostMapping("/{id}/delete")
    public String deleteFeature(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        featureFlagService.deleteFlag(id);
        redirectAttributes.addFlashAttribute("success", "Feature deleted.");
        return "redirect:/admin/features";
    }
}