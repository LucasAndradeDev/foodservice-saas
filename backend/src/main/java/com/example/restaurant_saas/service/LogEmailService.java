package com.example.restaurant_saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Writes the reset link to the application log instead of sending a real email.
 * Used in dev/test, and as the default until a real provider is configured — see
 * {@link BrevoEmailService} for the production implementation, selected via {@code app.email.provider}.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "log", matchIfMissing = true)
public class LogEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password reset requested for {}. Link: {}", toEmail, resetLink);
    }

    @Override
    public void sendPasswordChangedNotification(String toEmail) {
        log.info("Password changed notification for {}", toEmail);
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verifyLink) {
        log.info("Email verification requested for {}. Link: {}", toEmail, verifyLink);
    }
}
