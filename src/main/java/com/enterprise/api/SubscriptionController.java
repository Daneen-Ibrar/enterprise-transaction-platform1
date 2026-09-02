package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.subscription.CustomerSubscription;
import com.enterprise.subscription.SubscriptionPlan;
import com.enterprise.subscription.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/plans")
    public String listPlans(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // For now, get plans for tenant 1
        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(1L);
        model.addAttribute("plans", plans);
        return "subscriptions/plans";
    }

    @PostMapping("/subscribe")
    public String subscribe(@RequestParam Long planId, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // For merchant use case
        subscriptionService.createSubscription(
                user.getId(),
                user.getEmail(),
                1L,  // Merchant ID - should come from context
                planId
        );

        return "redirect:/subscriptions/my";
    }

    @GetMapping("/my")
    public String mySubscriptions(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<CustomerSubscription> subscriptions = subscriptionService.getCustomerSubscriptions(user.getId());
        model.addAttribute("subscriptions", subscriptions);
        return "subscriptions/my";
    }

    @PostMapping("/cancel/{id}")
    public String cancelSubscription(@PathVariable Long id) {
        subscriptionService.cancelSubscription(id);
        return "redirect:/subscriptions/my";
    }
}