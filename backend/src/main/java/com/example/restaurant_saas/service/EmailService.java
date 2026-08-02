package com.example.restaurant_saas.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
    void sendPasswordChangedNotification(String toEmail);
}
