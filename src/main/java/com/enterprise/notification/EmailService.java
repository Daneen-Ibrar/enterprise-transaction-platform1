package com.enterprise.notification;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
}