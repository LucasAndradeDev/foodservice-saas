package com.example.restaurant_saas.security;

import com.example.restaurant_saas.exception.TooManyLoginAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard for the login endpoint, keyed by IP + email so a
 * shared IP (e.g. a restaurant's wifi) can't lock out other accounts, and a single
 * account can't be locked out by attempts from an unrelated IP.
 */
@Component
public class LoginRateLimitService {

    private record Attempt(int count, Instant windowStart, Instant blockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attemptsByKey = new ConcurrentHashMap<>();

    @Value("${security.login-rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${security.login-rate-limit.window-minutes}")
    private long windowMinutes;

    @Value("${security.login-rate-limit.block-minutes}")
    private long blockMinutes;

    public void checkAllowed(HttpServletRequest request, String email) {
        Attempt attempt = attemptsByKey.get(key(request, email));
        if (attempt != null && attempt.blockedUntil() != null && Instant.now().isBefore(attempt.blockedUntil())) {
            throw new TooManyLoginAttemptsException("Too many failed login attempts. Try again in a few minutes.");
        }
    }

    public void recordFailure(HttpServletRequest request, String email) {
        Instant now = Instant.now();
        attemptsByKey.compute(key(request, email), (key, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(Duration.ofMinutes(windowMinutes)))) {
                return new Attempt(1, now, null);
            }

            int count = existing.count() + 1;
            Instant blockedUntil = count >= maxAttempts ? now.plus(Duration.ofMinutes(blockMinutes)) : null;
            return new Attempt(count, existing.windowStart(), blockedUntil);
        });
    }

    public void recordSuccess(HttpServletRequest request, String email) {
        attemptsByKey.remove(key(request, email));
    }

    private String key(HttpServletRequest request, String email) {
        return resolveClientIp(request) + "|" + email;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
