package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.refund.RefundService;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private static final Logger log = LoggerFactory.getLogger(AdminRefundController.class);

    private final RefundService refundService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AdminRefundController(RefundService refundService,
                                 UserRepository userRepository,
                                 TransactionRepository transactionRepository) {
        this.refundService = refundService;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    // ----- List settled transactions with refund eligibility -----
    @GetMapping
    public String listSettledTransactions(Model model) {
        log.info("=== AdminRefundController.listSettledTransactions() called ===");
        try {
            List<Transaction> settled = transactionRepository.findByStatus(TransactionStatus.SETTLED);
            log.info("Found {} settled transactions", settled.size());

            // Build a map of transaction ID → refundable (true/false)
            Map<Long, Boolean> refundableMap = new HashMap<>();
            for (Transaction tx : settled) {
                RefundService.RefundEligibility eligibility = refundService.evaluate(tx);
                boolean refundable = "ALLOW".equals(eligibility.getAction());
                refundableMap.put(tx.getId(), refundable);
            }

            model.addAttribute("transactions", settled);
            model.addAttribute("refundableMap", refundableMap);

            return "admin/refunds/list";
        } catch (Exception e) {
            log.error("ERROR in listSettledTransactions: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load refund list: " + e.getMessage());
            return "admin/refunds/list";
        }
    }

    // ----- Show refund form -----
    @GetMapping("/{transactionId}")
    public String showRefundForm(@PathVariable Long transactionId, Model model) {
        log.info("=== AdminRefundController.showRefundForm() called for transaction {}", transactionId);
        try {
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));
            if (transaction.getStatus() != TransactionStatus.SETTLED) {
                throw new IllegalStateException("Only settled transactions can be refunded");
            }
            model.addAttribute("transaction", transaction);
            return "admin/refunds/refund";
        } catch (Exception e) {
            log.error("ERROR in showRefundForm for transaction {}: {}", transactionId, e.getMessage(), e);
            model.addAttribute("error", "Failed to load refund form: " + e.getMessage());
            return "admin/refunds/refund";
        }
    }

    // ----- Process refund -----
    @PostMapping("/{transactionId}")
    public String processRefund(@PathVariable Long transactionId,
                                @RequestParam String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        log.info("=== AdminRefundController.processRefund() called for transaction {} with reason '{}'", transactionId, reason);
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            refundService.processRefund(transactionId, admin.getId(), reason);
            log.info("Refund processed successfully for transaction {}", transactionId);
            redirectAttributes.addFlashAttribute("success", "Refund processed successfully.");
            return "redirect:/admin/refunds";
        } catch (Exception e) {
            log.error("ERROR in processRefund for transaction {}: {}", transactionId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Refund failed: " + e.getMessage());
            return "redirect:/admin/refunds";
        }
    }
}