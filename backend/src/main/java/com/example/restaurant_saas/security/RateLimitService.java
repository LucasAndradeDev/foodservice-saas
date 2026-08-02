package com.example.restaurant_saas.security;

import com.example.restaurant_saas.exception.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory throttle for sensitive, unauthenticated endpoints (login, forgot-password),
 * keyed by action + IP + identifier (e.g. email) so a shared IP (e.g. a restaurant's wifi)
 * can't lock out other accounts, and a single account can't be locked out by attempts from
 * an unrelated IP. Thresholds are supplied per call so each action can have its own policy.
 */
@Component
public class RateLimitService {

    private record Attempt(int count, Instant windowStart, Instant blockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attemptsByKey = new ConcurrentHashMap<>();

    public void checkAllowed(String action, HttpServletRequest request, String identifier) {
        Attempt attempt = attemptsByKey.get(key(action, request, identifier));
        if (attempt != null && attempt.blockedUntil() != null && Instant.now().isBefore(attempt.blockedUntil())) {
            throw new TooManyAttemptsException("Too many attempts. Try again in a few minutes.");
        }
    }

    public void recordAttempt(
            String action, HttpServletRequest request, String identifier,
            int maxAttempts, long windowMinutes, long blockMinutes
    ) {
        Instant now = Instant.now();
        attemptsByKey.compute(key(action, request, identifier), (key, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(Duration.ofMinutes(windowMinutes)))) {
                return new Attempt(1, now, null);
            }

            int count = existing.count() + 1;
            Instant blockedUntil = count >= maxAttempts ? now.plus(Duration.ofMinutes(blockMinutes)) : null;
            return new Attempt(count, existing.windowStart(), blockedUntil);
        });
    }

    public void reset(String action, HttpServletRequest request, String identifier) {
        attemptsByKey.remove(key(action, request, identifier));
    }

    private String key(String action, HttpServletRequest request, String identifier) {
        return action + "|" + resolveClientIp(request) + "|" + identifier;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
