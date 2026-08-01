package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.RecurringSchedule;
import com.enterprise.invoice.RecurringScheduleService;
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

@Controller
@RequestMapping("/merchant/recurring")
@PreAuthorize("hasRole('MERCHANT')")
public class RecurringScheduleController {

    private static final Logger log = LoggerFactory.getLogger(RecurringScheduleController.class);
    private final RecurringScheduleService scheduleService;
    private final UserRepository userRepository;

    public RecurringScheduleController(RecurringScheduleService scheduleService,
                                       UserRepository userRepository) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
    }

    private Long getMerchantId(Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @GetMapping
    public String listSchedules(Authentication authentication, Model model) {
        Long merchantId = getMerchantId(authentication);
        List<RecurringSchedule> schedules = scheduleService.getSchedulesForMerchant(merchantId);
        model.addAttribute("schedules", schedules);
        return "merchant/recurring/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("schedule", new RecurringSchedule());
        model.addAttribute("frequencies", RecurringSchedule.Frequency.values());
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
        redirectAttributes.addFlashAttribute("success", "Recurring schedule created.");
        return "redirect:/merchant/recurring";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        RecurringSchedule schedule = scheduleService.getSchedule(id);
        model.addAttribute("schedule", schedule);
        model.addAttribute("frequencies", RecurringSchedule.Frequency.values());
        return "merchant/recurring/edit";
    }

    @PostMapping("/{id}")
    public String updateSchedule(@PathVariable Long id,
                                 @ModelAttribute RecurringSchedule schedule,
                                 RedirectAttributes redirectAttributes) {
        try {
            schedule.setId(id);
            scheduleService.updateSchedule(schedule);
            redirectAttributes.addFlashAttribute("success", "Schedule updated.");
            return "redirect:/merchant/recurring";
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure for schedule {}", id, e);
            redirectAttributes.addFlashAttribute("error",
                    "The schedule was modified by another process (e.g., the recurring scheduler). Please refresh the page and try again.");
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
        redirectAttributes.addFlashAttribute("success", "Schedule toggled.");
        return "redirect:/merchant/recurring";
    }

    @PostMapping("/{id}/delete")
    public String deleteSchedule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        scheduleService.deleteSchedule(id);
        redirectAttributes.addFlashAttribute("success", "Schedule deleted.");
        return "redirect:/merchant/recurring";
    }
}