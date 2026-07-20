package com.enterprise.invoice;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InvoiceMessageService {

    private final InvoiceMessageRepository messageRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public InvoiceMessageService(InvoiceMessageRepository messageRepository,
                                 NotificationService notificationService,
                                 UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public InvoiceMessage sendMessage(Invoice invoice, AppUser sender, String message) {
        InvoiceMessage msg = new InvoiceMessage(invoice, sender, message);
        msg = messageRepository.save(msg);

        boolean isMerchant = sender.getId().equals(invoice.getMerchantId());
        boolean isCustomer = invoice.getCustomerEmail().equals(sender.getEmail());
        boolean isAdmin = sender.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));

        List<AppUser> admins = userRepository.findByRolesName("ADMIN");

        // --- NOTIFICATION RULES ---
        // 1. Merchant → Notify ALL admins. Customer gets NO notification.
        // 2. Customer → Notify merchant AND all admins.
        // 3. Admin    → Notify merchant ONLY. Customer gets NO notification.
        // ---------------------------------

        if (isMerchant) {
            // Merchant → Admins only
            admins.forEach(admin -> notificationService.createNotification(
                admin.getId(),
                "CHAT_MESSAGE",
                "New Chat Message from Merchant",
                "Merchant sent a message about Invoice #" + invoice.getId(),
                "/admin/invoices/" + invoice.getId() + "/messages"
            ));
        } else if (isCustomer) {
            // Customer → Merchant + Admins
            notificationService.createNotification(
                invoice.getMerchantId(),
                "CHAT_MESSAGE",
                "New Chat Message from Customer",
                "Customer sent a message about Invoice #" + invoice.getId(),
                "/invoices/" + invoice.getId() + "/messages"
            );
            admins.forEach(admin -> notificationService.createNotification(
                admin.getId(),
                "CHAT_MESSAGE",
                "New Chat Message from Customer",
                "Customer sent a message about Invoice #" + invoice.getId(),
                "/admin/invoices/" + invoice.getId() + "/messages"
            ));
        } else if (isAdmin) {
            // Admin → Merchant only
            notificationService.createNotification(
                invoice.getMerchantId(),
                "CHAT_MESSAGE",
                "New Chat Message from Admin",
                "Admin replied about Invoice #" + invoice.getId(),
                "/invoices/" + invoice.getId() + "/messages"
            );
            // Customer does NOT get this notification
        }

        return msg;
    }

    public List<InvoiceMessage> getMessagesForInvoice(Long invoiceId) {
        return messageRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }
}