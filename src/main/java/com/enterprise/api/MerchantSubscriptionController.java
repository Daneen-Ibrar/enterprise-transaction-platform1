package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.subscription.CustomerSubscription;
import com.enterprise.subscription.SubscriptionPlan;
import com.enterprise.subscription.SubscriptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/merchant/subscriptions")
public class MerchantSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public MerchantSubscriptionController(SubscriptionService subscriptionService,
                                          UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String dashboard(Model model, Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long merchantId = merchant.getId();

        // Stats
        Map<String, Object> stats = subscriptionService.getMerchantStats(merchantId);
        model.addAttribute("stats", stats);

        // Recent subscriptions
        Page<CustomerSubscription> subscriptions = subscriptionService.getMerchantSubscriptionsPaginated(
                merchantId, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("subscriptions", subscriptions);

        // Plans
        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(merchant.getTenantId());
        model.addAttribute("plans", plans);

        return "merchant/subscriptions/dashboard";
    }

    @GetMapping("/plans")
    public String managePlans(Model model, Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(merchant.getTenantId());
        model.addAttribute("plans", plans);
        return "merchant/subscriptions/plans";
    }

    @PostMapping("/plans/create")
    public String createPlan(@ModelAttribute SubscriptionPlan plan,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.createPlan(plan);
            redirectAttributes.addFlashAttribute("success", "Plan created successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create plan: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions/plans";
    }

    @PostMapping("/plans/{id}/update")
    public String updatePlan(@PathVariable Long id,
                             @ModelAttribute SubscriptionPlan plan,
                             RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.updatePlan(id, plan);
            redirectAttributes.addFlashAttribute("success", "Plan updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update plan: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions/plans";
    }

    @PostMapping("/plans/{id}/delete")
    public String deletePlan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.deletePlan(id);
            redirectAttributes.addFlashAttribute("success", "Plan deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete plan: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions/plans";
    }

    @GetMapping("/customers")
    public String customerSubscriptions(Model model, Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<CustomerSubscription> subscriptions = subscriptionService.getMerchantSubscriptionsPaginated(
                merchant.getId(), PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("subscriptions", subscriptions);
        return "merchant/subscriptions/customers";
    }

    @PostMapping("/{id}/upgrade")
    public String upgradeSubscription(@PathVariable Long id,
                                      @RequestParam Long planId,
                                      RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.upgradeSubscription(id, planId);
            redirectAttributes.addFlashAttribute("success", "Subscription upgraded successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upgrade: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions";
    }

    @PostMapping("/{id}/downgrade")
    public String downgradeSubscription(@PathVariable Long id,
                                        @RequestParam Long planId,
                                        RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.downgradeSubscription(id, planId);
            redirectAttributes.addFlashAttribute("success", "Subscription downgraded successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to downgrade: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions";
    }

    @PostMapping("/{id}/cancel")
    public String cancelSubscription(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.cancelSubscription(id);
            redirectAttributes.addFlashAttribute("success", "Subscription cancelled successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to cancel: " + e.getMessage());
        }
        return "redirect:/merchant/subscriptions";
    }
}