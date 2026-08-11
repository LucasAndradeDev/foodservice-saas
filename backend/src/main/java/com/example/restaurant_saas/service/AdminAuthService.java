package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.AdminCredentials;
import com.example.restaurant_saas.domain.entity.AdminPasswordResetToken;
import com.example.restaurant_saas.dto.request.AdminLoginRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.response.AdminAuthResponse;
import com.example.restaurant_saas.repository.AdminCredentialsRepository;
import com.example.restaurant_saas.repository.AdminPasswordResetTokenRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String ADMIN_LOGIN_ACTION = "admin-login";
    private static final String ADMIN_FORGOT_PASSWORD_ACTION = "admin-forgot-password";

    private final AdminCredentialsRepository adminCredentialsRepository;
    private final AdminPasswordResetTokenRepository adminPasswordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final EmailService emailService;
    private final HttpServletRequest httpRequest;

    @Value("${security.admin-login-rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${security.admin-login-rate-limit.window-minutes}")
    private long windowMinutes;

    @Value("${security.admin-login-rate-limit.block-minutes}")
    private long blockMinutes;

    @Value("${security.admin-forgot-password-rate-limit.max-attempts}")
    private int forgotPasswordMaxAttempts;

    @Value("${security.admin-forgot-password-rate-limit.window-minutes}")
    private long forgotPasswordWindowMinutes;

    @Value("${security.admin-forgot-password-rate-limit.block-minutes}")
    private long forgotPasswordBlockMinutes;

    @Value("${security.password-reset-token.expiration-minutes}")
    private long passwordResetTokenExpirationMinutes;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AdminAuthResponse login(AdminLoginRequest request) {
        rateLimitService.checkAllowed(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername());

        AdminCredentials credentials = adminCredentialsRepository.findFirstByOrderByCreatedAtAsc().orElse(null);

        boolean valid = credentials != null
                && credentials.getUsername().equalsIgnoreCase(request.getUsername())
                && passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash());

        if (!valid) {
            rateLimitService.recordAttempt(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername(), maxAttempts, windowMinutes, blockMinutes);
            throw new IllegalArgumentException("Invalid admin credentials.");
        }

        rateLimitService.reset(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername());

        return buildAuthResponse(credentials);
    }

    @Transactional
    public void forgotPassword() {
        // No email input: there's only ever one admin, so this always targets the email
        // already on file for it. Still rate-limited per IP to prevent spamming that inbox.
        rateLimitService.checkAllowed(ADMIN_FORGOT_PASSWORD_ACTION, httpRequest, "admin");
        rateLimitService.recordAttempt(
                ADMIN_FORGOT_PASSWORD_ACTION, httpRequest, "admin",
                forgotPasswordMaxAttempts, forgotPasswordWindowMinutes, forgotPasswordBlockMinutes
        );

        adminCredentialsRepository.findFirstByOrderByCreatedAtAsc()
                .filter(credentials -> credentials.getEmail() != null)
                .ifPresent(credentials -> {
                    adminPasswordResetTokenRepository.deleteAll();

                    AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                            .token(UUID.randomUUID().toString())
                            .expiryDate(OffsetDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes))
                            .used(false)
                            .build();
                    resetToken = adminPasswordResetTokenRepository.save(resetToken);

                    String resetLink = frontendUrl + "/admin/reset-password?token=" + resetToken.getToken();
                    try {
                        emailService.sendPasswordResetEmail(credentials.getEmail(), resetLink);
                    } catch (RuntimeException ex) {
                        log.error("Failed to send admin password reset email", ex);
                    }
                });
    }

    @Transactional
    public AdminAuthResponse resetPassword(ResetPasswordRequest request) {
        AdminPasswordResetToken resetToken = adminPasswordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link."));

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        AdminCredentials credentials = adminCredentialsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found."));

        credentials.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminCredentialsRepository.save(credentials);

        resetToken.setUsed(true);
        adminPasswordResetTokenRepository.save(resetToken);

        if (credentials.getEmail() != null) {
            try {
                emailService.sendPasswordChangedNotification(credentials.getEmail());
            } catch (RuntimeException ex) {
                log.error("Failed to send admin password changed notification", ex);
            }
        }

        return buildAuthResponse(credentials);
    }

    private AdminAuthResponse buildAuthResponse(AdminCredentials credentials) {
        return AdminAuthResponse.builder()
                .accessToken(jwtService.generateAdminToken(credentials.getUsername()))
                .tokenType("Bearer")
                .build();
    }
}
