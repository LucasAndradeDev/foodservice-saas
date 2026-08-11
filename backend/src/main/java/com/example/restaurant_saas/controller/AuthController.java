package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.ChangePasswordRequest;
import com.example.restaurant_saas.dto.request.ForgotPasswordRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.request.VerifyEmailRequest;
import com.example.restaurant_saas.dto.response.AuthResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The refresh token is never present in a JSON response body (see AuthResponse#refreshToken) —
 * it only ever travels as an httpOnly cookie, set/cleared here. That keeps it out of reach of
 * any JS running on the page (including an XSS payload), unlike the access token, which is a
 * short-lived bearer token the frontend keeps in memory and attaches itself.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    // Scoped narrowly to the endpoints that actually need it, so the cookie isn't sent on every
    // other API call.
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    @Value("${api.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/register-restaurant")
    public ResponseEntity<AuthResponse> registerRestaurant(@Valid @RequestBody RegisterRestaurantRequest request) {
        AuthResponse response = authService.registerRestaurant(request);
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED), response.getRefreshToken()).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withRefreshCookie(ResponseEntity.ok(), response.getRefreshToken()).body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthResponse response = authService.resetPassword(request);
        return withRefreshCookie(ResponseEntity.ok(), response.getRefreshToken()).body(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token not found.");
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(currentUser.getId(), refreshToken);
        }
        return withClearedRefreshCookie(ResponseEntity.status(HttpStatus.NO_CONTENT)).build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUser.getId(), request);
        return withClearedRefreshCookie(ResponseEntity.status(HttpStatus.NO_CONTENT)).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getMe(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        AuthResponse response = authService.getMe(currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        authService.resendVerificationEmail(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    private <B extends ResponseEntity.HeadersBuilder<B>> B withRefreshCookie(B builder, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(refreshExpirationMs / 1000)
                .build();
        return builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private <B extends ResponseEntity.HeadersBuilder<B>> B withClearedRefreshCookie(B builder) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        return builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
