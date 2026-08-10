package com.enterprise.api;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin/bulk")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminBulkViewController {

    private final InvoiceService invoiceService;
    private final TransactionRepository transactionRepository;

    public AdminBulkViewController(InvoiceService invoiceService,
                                   TransactionRepository transactionRepository) {
        this.invoiceService = invoiceService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public String bulkPage(Model model) {
        List<Invoice> pendingInvoices = invoiceService.getPendingApprovalInvoices();
        model.addAttribute("pendingInvoices", pendingInvoices);

        List<Transaction> settledTransactions = transactionRepository.findByStatus(TransactionStatus.SETTLED);
        model.addAttribute("settledTransactions", settledTransactions);

        return "admin/bulk";
    }
}