package com.enterprise.invoice;

import com.enterprise.identity.AppUser;
import com.enterprise.notification.NotificationService;
import com.enterprise.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrivateMessageService {

    private final PrivateMessageRepository privateMessageRepository;
    private final NotificationService notificationService;

    public PrivateMessageService(PrivateMessageRepository privateMessageRepository,
                                 NotificationService notificationService) {
        this.privateMessageRepository = privateMessageRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public PrivateMessage sendPrivateMessage(Invoice invoice, AppUser sender, AppUser recipient, String message) {
        PrivateMessage pm = new PrivateMessage(invoice, sender, recipient, message);

        // ✅ FIX: Use required tenant ID – fail closed
        pm.setTenantId(TenantContext.getRequiredTenantId());

        pm = privateMessageRepository.save(pm);

        notificationService.createNotification(
            recipient.getId(),
            "PRIVATE_MESSAGE",
            "New Private Message",
            "You have a new private message regarding Invoice #" + invoice.getId(),
            "/private-chat/" + invoice.getId() + "?withUserId=" + sender.getId()
        );

        return pm;
    }

    public List<PrivateMessage> getMessagesBetweenUsers(Long invoiceId, Long userId1, Long userId2) {
        return privateMessageRepository.findMessagesBetweenUsers(invoiceId, userId1, userId2);
    }
}