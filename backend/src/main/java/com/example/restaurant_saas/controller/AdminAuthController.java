package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.AdminLoginRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.response.AdminAuthResponse;
import com.example.restaurant_saas.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Platform admin login — separate from restaurant staff accounts")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "Admin login", description = "Platform-admin credentials, unrelated to any restaurant's users.")
    public ResponseEntity<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Admin forgot password", description = "Sends a reset link to the platform admin's configured email. There's only ever one admin, so no email input is needed.")
    public ResponseEntity<Void> forgotPassword() {
        adminAuthService.forgotPassword();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Admin reset password", description = "Consumes a reset token and sets a new admin password.")
    public ResponseEntity<AdminAuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(adminAuthService.resetPassword(request));
    }
}
