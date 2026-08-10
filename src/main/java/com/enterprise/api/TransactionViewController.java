package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/transactions")
@PreAuthorize("isAuthenticated()")
public class TransactionViewController {

    private static final Logger log = LoggerFactory.getLogger(TransactionViewController.class);

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionViewController(TransactionRepository transactionRepository,
                                     UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        try {
            Transaction tx = transactionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));
            model.addAttribute("transaction", tx);
            return "transactions/detail";
        } catch (Exception e) {
            log.error("Error loading transaction detail for id {}: {}", id, e.getMessage());
            model.addAttribute("error", "Transaction not found.");
            return "error";
        }
    }

    @GetMapping
    public String listTransactions(Authentication authentication, Model model) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return "redirect:/login";
            }

            AppUser user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("SUPER_ADMIN"));
            boolean isMerchant = user.getRoles().stream().anyMatch(r -> r.getName().equals("MERCHANT"));
            boolean isCustomer = user.getRoles().stream().anyMatch(r -> r.getName().equals("CUSTOMER"));

            List<Transaction> transactions;

            if (isSuperAdmin) {
                // 👇 Super Admin sees ALL transactions
                transactions = transactionRepository.findAll();
            } else if (isMerchant) {
                transactions = transactionRepository.findByMerchantIdOrderByCreatedAtDesc(user.getId());
            } else if (isCustomer) {
                transactions = transactionRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId());
            } else {
                log.warn("User {} has no recognized role, showing empty transaction list", user.getEmail());
                transactions = List.of();
                model.addAttribute("info", "You do not have any transactions to view.");
            }

            model.addAttribute("transactions", transactions);
            model.addAttribute("userRole", isSuperAdmin ? "SUPER_ADMIN" : isMerchant ? "MERCHANT" : isCustomer ? "CUSTOMER" : "UNKNOWN");
            return "transactions/list";

        } catch (Exception e) {
            log.error("Error loading transaction list: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load transactions: " + e.getMessage());
            model.addAttribute("transactions", List.of());
            return "transactions/list";
        }
    }
}