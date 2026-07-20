package com.enterprise.notification;

import java.math.BigDecimal;

public class EmailTemplates {

    public static String paymentConfirmation(String customerName, Long invoiceId, Long transactionId, BigDecimal amount, String currency) {
        String symbol = switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> "£";
        };
        return String.format("""
            Dear Customer,

            Your payment of %s%.2f for Invoice #%d has been confirmed.
            Transaction ID: %d
            Currency: %s

            Thank you for your business.

            Regards,
            Enterprise Transaction Platform
            """, symbol, amount, invoiceId, transactionId, currency);
    }

    public static String invoiceApproved(Long invoiceId, BigDecimal amount, String merchantName) {
        return String.format("""
            Dear Merchant,

            Your Invoice #%d for £%.2f has been approved by the admin.
            You can now share the payment link with your customer.

            Regards,
            Enterprise Transaction Platform
            """, invoiceId, amount);
    }

    public static String invoiceRejected(Long invoiceId, String reason) {
        return String.format("""
            Dear Merchant,

            Your Invoice #%d has been rejected.
            Reason: %s

            If you wish to appeal, please use the chat feature in the dashboard.

            Regards,
            Enterprise Transaction Platform
            """, invoiceId, reason);
    }

    public static String refundIssued(Long transactionId, BigDecimal amount, String reason) {
        return String.format("""
            Dear Customer,

            A refund of £%.2f has been issued for Transaction #%d.
            Reason: %s

            The refund will be processed to your original payment method.

            Regards,
            Enterprise Transaction Platform
            """, amount, transactionId, reason);
    }

    public static String chatMessageNotification(Long invoiceId, String senderName, String messagePreview) {
        return String.format("""
            Dear User,

            You have a new chat message regarding Invoice #%d.
            From: %s
            Message: %s

            Please log in to view and reply.

            Regards,
            Enterprise Transaction Platform
            """, invoiceId, senderName, messagePreview);
    }
}