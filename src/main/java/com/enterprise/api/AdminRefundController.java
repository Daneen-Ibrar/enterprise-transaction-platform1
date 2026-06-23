package com.enterprise.api;

import com.enterprise.refund.RefundService;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final TransactionRepository transactionRepository;
    private final RefundService refundService;

    public AdminRefundController(TransactionRepository transactionRepository,
                                 RefundService refundService) {
        this.transactionRepository = transactionRepository;
        this.refundService = refundService;
    }

    @GetMapping
    public String listSettledTransactions(Model model) {
        List<Transaction> settled = transactionRepository.findByStatus(
                com.enterprise.transaction.TransactionStatus.SETTLED);
        model.addAttribute("transactions", settled);
        return "admin/refund/list";
    }

    @GetMapping("/{id}")
    public String showRefundForm(@PathVariable Long id, Model model) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (tx.getStatus() != com.enterprise.transaction.TransactionStatus.SETTLED) {
            throw new IllegalStateException("Only settled transactions can be refunded");
        }
        model.addAttribute("transaction", tx);
        return "admin/refund/form";
    }

    @PostMapping("/{id}")
    public String processRefund(@PathVariable Long id,
                                @RequestParam String reason,
                                Authentication authentication) {
        Long adminId = 1L; // In real app, fetch from authentication.
        refundService.processRefund(id, reason, adminId);
        return "redirect:/admin/refunds?success";
    }
}