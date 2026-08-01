package com.enterprise.notification;

import java.math.BigDecimal;

public class EmailTemplates {

    public static String paymentConfirmationHtml(String customerName, Long invoiceId, Long transactionId, BigDecimal amount, String currency) {
        String symbol = switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> "£";
        };
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Payment Confirmation</h2>
                <p>Dear %s,</p>
                <p>Your payment of <strong>%s%.2f</strong> for Invoice #%d has been confirmed.</p>
                <p>Transaction ID: <strong>%d</strong></p>
                <p>Currency: %s</p>
                <p>Thank you for your business.</p>
                <br/>
                <p>Regards,<br/>Enterprise Transaction Platform</p>
            </body>
            </html>
            """.formatted(customerName, symbol, amount, invoiceId, transactionId, currency);
    }

    // Add other templates (invoiceApproved, invoiceRejected, refundIssued, chatMessageNotification) as HTML.
}