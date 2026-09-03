package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.subscription.CustomerSubscription;
import com.enterprise.subscription.SubscriptionPlan;
import com.enterprise.subscription.SubscriptionRequest;
import com.enterprise.subscription.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/subscriptions")
public class CustomerSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public CustomerSubscriptionController(SubscriptionService subscriptionService,
                                          UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    // ============================================================
    // VIEW ALL SUBSCRIPTIONS FOR CURRENT CUSTOMER
    // ============================================================
    @GetMapping("/my")
    public String mySubscriptions(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<CustomerSubscription> subscriptions = subscriptionService.getCustomerSubscriptions(user.getId());
        List<SubscriptionRequest> requests = subscriptionService.getRequestsForCustomer(user.getId());
        
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("requests", requests);
        model.addAttribute("customerId", user.getId());
        model.addAttribute("customerEmail", user.getEmail());
        
        return "subscriptions/my";
    }

    // ============================================================
    // VIEW SUBSCRIPTION DETAILS
    // ============================================================
    @GetMapping("/{id}")
    public String subscriptionDetail(@PathVariable Long id, Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomerSubscription subscription = subscriptionService.getSubscriptionById(id);
        
        if (!subscription.getCustomerId().equals(user.getId())) {
            throw new RuntimeException("You don't have permission to view this subscription");
        }

        model.addAttribute("subscription", subscription);
        return "subscriptions/detail";
    }

    // ============================================================
    // CANCEL SUBSCRIPTION
    // ============================================================
    @PostMapping("/{id}/cancel")
    public String cancelSubscription(@PathVariable Long id, 
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomerSubscription subscription = subscriptionService.getSubscriptionById(id);
        
        if (!subscription.getCustomerId().equals(user.getId())) {
            throw new RuntimeException("You don't have permission to cancel this subscription");
        }

        try {
            subscriptionService.cancelSubscription(id);
            redirectAttributes.addFlashAttribute("success", "Your subscription has been cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to cancel subscription: " + e.getMessage());
        }
        
        return "redirect:/subscriptions/my";
    }

    // ============================================================
    // AVAILABLE PLANS (For Customers)
    // ============================================================
    @GetMapping("/plans")
    public String availablePlans(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(user.getTenantId());
        model.addAttribute("plans", plans);
        return "subscriptions/plans";
    }

    // ============================================================
    // SUBSCRIBE TO A PLAN - Creates a REQUEST
    // ============================================================
    @PostMapping("/subscribe")
    public String subscribe(@RequestParam Long planId,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            AppUser user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Get the plan
            SubscriptionPlan plan = subscriptionService.getPlanById(planId);
            
            // ✅ Find a merchant in the same tenant
            List<AppUser> merchants = userRepository.findByTenantIdAndRoleName(user.getTenantId(), "MERCHANT");
            
            if (merchants.isEmpty()) {
                // ✅ If no merchant found, use a default merchant ID or show error
                // For now, try to find any admin or merchant
                List<AppUser> admins = userRepository.findByTenantIdAndRoleName(user.getTenantId(), "SUPER_ADMIN");
                if (admins.isEmpty()) {
                    // Last resort - use the first user with any role
                    List<AppUser> allUsers = userRepository.findByTenantId(user.getTenantId());
                    if (allUsers.isEmpty()) {
                        throw new RuntimeException("No merchant or admin available. Please contact support.");
                    }
                    // Use the first user as the merchant
                    Long merchantId = allUsers.get(0).getId();
                    subscriptionService.createRequest(
                            user.getId(),
                            user.getEmail(),
                            merchantId,
                            planId
                        );
                    } else {
                        Long merchantId = admins.get(0).getId();
                        subscriptionService.createRequest(
                            user.getId(),
                            user.getEmail(),
                            merchantId,
                            planId
                        );
                    }
            } else {
                // Use the first merchant found
                Long merchantId = merchants.get(0).getId();
                subscriptionService.createRequest(
                    user.getId(),
                    user.getEmail(),
                    merchantId,
                    planId
                );
            }
            
            redirectAttributes.addFlashAttribute("success", 
                    "✅ Subscription request submitted for '" + plan.getName() + "'! Awaiting admin approval.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to subscribe: " + e.getMessage());
        }
        
        return "redirect:/subscriptions/my";
    }
}