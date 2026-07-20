package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.invoice.PrivateMessage;
import com.enterprise.invoice.PrivateMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/private-chat")
public class PrivateChatController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final PrivateMessageService privateMessageService;

    public PrivateChatController(InvoiceService invoiceService,
                                 UserRepository userRepository,
                                 PrivateMessageService privateMessageService) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.privateMessageService = privateMessageService;
    }

    @GetMapping("/{invoiceId}")
    public String chatPage(@PathVariable Long invoiceId,
                           @RequestParam(required = false) Long withUserId,
                           @RequestParam(required = false) String withEmail,
                           Authentication authentication,
                           Model model) {
        AppUser currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if current user is active
        if (!currentUser.isActive()) {
            model.addAttribute("error", "Your account has been disabled. You cannot access chat.");
            return "private-chat";
        }

        Invoice invoice = invoiceService.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // Authorisation: only merchant, customer, or admin can view this chat
        boolean isMerchant = currentUser.getId().equals(invoice.getMerchantId());
        boolean isCustomer = invoice.getCustomerEmail().equals(currentUser.getEmail());
        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (!(isMerchant || isCustomer || isAdmin)) {
            throw new RuntimeException("Unauthorized to view this chat");
        }

        AppUser otherUser;
        if (withUserId != null) {
            otherUser = userRepository.findById(withUserId)
                    .orElseThrow(() -> new RuntimeException("Other user not found"));
        } else if (withEmail != null) {
            otherUser = userRepository.findByEmail(withEmail)
                    .orElseThrow(() -> new RuntimeException("Other user not found"));
        } else {
            throw new RuntimeException("Missing participant identifier");
        }

        // Check if other user is active
        if (!otherUser.isActive()) {
            model.addAttribute("error", "The user you are trying to chat with has been disabled.");
            return "private-chat";
        }

        // Ensure the other user is a valid participant
        boolean otherIsMerchant = otherUser.getId().equals(invoice.getMerchantId());
        boolean otherIsCustomer = invoice.getCustomerEmail().equals(otherUser.getEmail());
        boolean otherIsAdmin = otherUser.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (!(otherIsMerchant || otherIsCustomer || otherIsAdmin)) {
            throw new RuntimeException("Invalid chat participant");
        }

        List<PrivateMessage> messages = privateMessageService.getMessagesBetweenUsers(invoiceId, currentUser.getId(), otherUser.getId());

        model.addAttribute("invoice", invoice);
        model.addAttribute("otherUser", otherUser);
        model.addAttribute("messages", messages);
        model.addAttribute("currentUserId", currentUser.getId());

        return "private-chat";
    }

    @PostMapping("/{invoiceId}/send")
    public String sendMessage(@PathVariable Long invoiceId,
                              @RequestParam Long recipientId,
                              @RequestParam String message,
                              Authentication authentication) {
        AppUser sender = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Invoice invoice = invoiceService.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        AppUser recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Check if sender or recipient is active
        if (!sender.isActive()) {
            throw new RuntimeException("Your account has been disabled. You cannot send messages.");
        }
        if (!recipient.isActive()) {
            throw new RuntimeException("The recipient's account has been disabled.");
        }

        privateMessageService.sendPrivateMessage(invoice, sender, recipient, message);
        return "redirect:/private-chat/" + invoiceId + "?withUserId=" + recipientId;
    }
}