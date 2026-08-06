package com.example.restaurant_saas.service;

import com.example.restaurant_saas.dto.request.AdminLoginRequest;
import com.example.restaurant_saas.dto.response.AdminAuthResponse;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String ADMIN_LOGIN_ACTION = "admin-login";

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpRequest;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password-hash}")
    private String adminPasswordHash;

    @Value("${security.admin-login-rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${security.admin-login-rate-limit.window-minutes}")
    private long windowMinutes;

    @Value("${security.admin-login-rate-limit.block-minutes}")
    private long blockMinutes;

    public AdminAuthResponse login(AdminLoginRequest request) {
        rateLimitService.checkAllowed(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername());

        boolean valid = adminUsername != null
                && !adminUsername.isBlank()
                && adminUsername.equalsIgnoreCase(request.getUsername())
                && adminPasswordHash != null
                && !adminPasswordHash.isBlank()
                && passwordEncoder.matches(request.getPassword(), adminPasswordHash);

        if (!valid) {
            rateLimitService.recordAttempt(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername(), maxAttempts, windowMinutes, blockMinutes);
            throw new IllegalArgumentException("Invalid admin credentials.");
        }

        rateLimitService.reset(ADMIN_LOGIN_ACTION, httpRequest, request.getUsername());

        return AdminAuthResponse.builder()
                .accessToken(jwtService.generateAdminToken(adminUsername))
                .tokenType("Bearer")
                .build();
    }
}
