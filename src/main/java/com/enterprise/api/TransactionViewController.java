package com.enterprise.api;

import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANT', 'CUSTOMER')")
public class TransactionViewController {

    private final TransactionRepository transactionRepository;

    public TransactionViewController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        model.addAttribute("transaction", tx);
        return "transactions/detail";
    }
}