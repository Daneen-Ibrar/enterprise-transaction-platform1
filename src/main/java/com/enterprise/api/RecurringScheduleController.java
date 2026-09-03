package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.RecurringSchedule;
import com.enterprise.invoice.RecurringScheduleService;
import com.enterprise.subscription.CustomerSubscription;
import com.enterprise.subscription.SubscriptionPlan;
import com.enterprise.subscription.SubscriptionService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/merchant/recurring")
@PreAuthorize("hasRole('MERCHANT')")
public class RecurringScheduleController {

    private static final Logger log = LoggerFactory.getLogger(RecurringScheduleController.class);

    private final RecurringScheduleService scheduleService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public RecurringScheduleController(RecurringScheduleService scheduleService,
                                       SubscriptionService subscriptionService,
                                       UserRepository userRepository) {
        this.scheduleService = scheduleService;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    private Long getMerchantId(Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    // ============================================================
    // RECURRING SCHEDULES (Existing)
    // ============================================================

    @GetMapping
    public String listSchedules(Authentication authentication, Model model) {
        Long merchantId = getMerchantId(authentication);
        List<RecurringSchedule> schedules = scheduleService.getSchedulesForMerchant(merchantId);
        
        // Get subscription stats
        Map<String, Object> stats = subscriptionService.getMerchantStats(merchantId);
        
        model.addAttribute("schedules", schedules);
        model.addAttribute("stats", stats);
        model.addAttribute("activeSubscriptions", stats.get("activeSubscriptions"));
        model.addAttribute("monthlyRevenue", stats.get("monthlyRevenue"));
        model.addAttribute("viewType", "recurring");
        
        return "merchant/recurring/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("schedule", new RecurringSchedule());
        model.addAttribute("frequencies", RecurringSchedule.Frequency.values());
        model.addAttribute("action", "create");
        return "merchant/recurring/create";
    }

    @PostMapping
    public String createSchedule(@ModelAttribute RecurringSchedule schedule,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        schedule.setMerchantId(getMerchantId(authentication));
        schedule.setTenantId(TenantContext.getRequiredTenantId());
        if (schedule.getStartDate() == null) {
            schedule.setStartDate(LocalDate.now());
        }
        scheduleService.createSchedule(schedule);
        redirectAttributes.addFlashAttribute("success", "Recurring schedule created successfully.");
        return "redirect:/merchant/recurring";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        RecurringSchedule schedule = scheduleService.getSchedule(id);
        model.addAttribute("schedule", schedule);
        model.addAttribute("frequencies", RecurringSchedule.Frequency.values());
        model.addAttribute("action", "edit");
        return "merchant/recurring/edit";
    }

    @PostMapping("/{id}")
    public String updateSchedule(@PathVariable Long id,
                                 @ModelAttribute RecurringSchedule schedule,
                                 RedirectAttributes redirectAttributes) {
        try {
            schedule.setId(id);
            scheduleService.updateSchedule(schedule);
            redirectAttributes.addFlashAttribute("success", "Schedule updated successfully.");
            return "redirect:/merchant/recurring";
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure for schedule {}", id, e);
            redirectAttributes.addFlashAttribute("error",
                    "The schedule was modified by another process. Please refresh and try again.");
            return "redirect:/merchant/recurring/" + id + "/edit";
        } catch (Exception e) {
            log.error("Error updating schedule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update schedule: " + e.getMessage());
            return "redirect:/merchant/recurring/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggleSchedule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        scheduleService.toggleActive(id);
        redirectAttributes.addFlashAttribute("success", "Schedule toggled successfully.");
        return "redirect:/merchant/recurring";
    }

    @PostMapping("/{id}/delete")
    public String deleteSchedule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        scheduleService.deleteSchedule(id);
        redirectAttributes.addFlashAttribute("success", "Schedule deleted successfully.");
        return "redirect:/merchant/recurring";
    }

    // ============================================================
    // SUBSCRIPTION PLANS (New)
    // ============================================================

    @GetMapping("/plans")
    public String listPlans(Model model, Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(merchant.getTenantId());
        model.addAttribute("plans", plans);
        return "merchant/recurring/plans";
    }

    @PostMapping("/plans/create")
    public String createPlan(@ModelAttribute SubscriptionPlan plan,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser merchant = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            plan.setTenantId(merchant.getTenantId());
            subscriptionService.createPlan(plan);
            redirectAttributes.addFlashAttribute("success", "Plan created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create plan: " + e.getMessage());
        }
        return "redirect:/merchant/recurring/plans";
    }

    @PostMapping("/plans/{id}/update")
    public String updatePlan(@PathVariable Long id,
                             @ModelAttribute SubscriptionPlan plan,
                             RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.updatePlan(id, plan);
            redirectAttributes.addFlashAttribute("success", "Plan updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update plan: " + e.getMessage());
        }
        return "redirect:/merchant/recurring/plans";
    }

    @PostMapping("/plans/{id}/delete")
    public String deletePlan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.deletePlan(id);
            redirectAttributes.addFlashAttribute("success", "Plan deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete plan: " + e.getMessage());
        }
        return "redirect:/merchant/recurring/plans";
    }

    // ============================================================
    // SUBSCRIPTIONS (New)
    // ============================================================

    @GetMapping("/subscriptions")
    public String listSubscriptions(Model model, Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<CustomerSubscription> subscriptions = subscriptionService.getMerchantSubscriptions(merchant.getId());
        Map<String, Object> stats = subscriptionService.getMerchantStats(merchant.getId());
        List<SubscriptionPlan> plans = subscriptionService.getPlansForTenant(merchant.getTenantId());
        
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("stats", stats);
        model.addAttribute("plans", plans);
        return "merchant/recurring/subscriptions";
    }

    @PostMapping("/subscriptions/{id}/upgrade")
    public String upgradeSubscription(@PathVariable Long id,
                                      @RequestParam Long planId,
                                      RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.upgradeSubscription(id, planId);
            redirectAttributes.addFlashAttribute("success", "Subscription upgraded successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upgrade: " + e.getMessage());
        }
        return "redirect:/merchant/recurring/subscriptions";
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public String cancelSubscription(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subscriptionService.cancelSubscription(id);
            redirectAttributes.addFlashAttribute("success", "Subscription cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to cancel: " + e.getMessage());
        }
        return "redirect:/merchant/recurring/subscriptions";
    }
}