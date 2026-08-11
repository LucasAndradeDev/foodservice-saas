package com.example.restaurant_saas.security;

import com.example.restaurant_saas.exception.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory throttle for sensitive, unauthenticated endpoints (login, forgot-password),
 * keyed by action + IP + identifier (e.g. email) so a shared IP (e.g. a restaurant's wifi)
 * can't lock out other accounts, and a single account can't be locked out by attempts from
 * an unrelated IP. Thresholds are supplied per call so each action can have its own policy.
 *
 * <p>On top of that per-identifier limit, every check/record also enforces a per-IP-only ceiling
 * (ignoring the identifier entirely), at a higher threshold. Without it, an endpoint whose
 * identifier is itself attacker-controlled and free-form (e.g. reservation-create is keyed by
 * the caller-supplied phone number) would face zero real throttling: a fresh identifier value
 * always starts a fresh bucket, so an attacker who simply varies the phone number on every
 * request never accumulates a count anywhere. The per-identifier limit still does its job of not
 * locking out an innocent shared IP; the IP-only ceiling is the backstop for the case it can't
 * cover.
 */
@Component
public class RateLimitService {

    // How much higher the IP-only ceiling is than the per-identifier one it backstops - loose
    // enough that a legitimate shared IP (several genuine customers/staff) isn't the one that
    // trips it, tight enough to still cap an attacker rotating identifiers to something well
    // short of unlimited.
    private static final int IP_ONLY_LIMIT_MULTIPLIER = 5;
    private static final String IP_ONLY_IDENTIFIER = "*";

    // staleAt marks when this entry stops being useful for throttling (end of its window, or end
    // of its block period if later) - the scheduled sweep below uses it to bound map growth.
    private record Attempt(int count, Instant windowStart, Instant blockedUntil, Instant staleAt) {
    }

    private final ConcurrentHashMap<String, Attempt> attemptsByKey = new ConcurrentHashMap<>();

    public void checkAllowed(String action, HttpServletRequest request, String identifier) {
        checkKey(key(action, request, identifier));
        checkKey(ipOnlyKey(action, request));
    }

    public void recordAttempt(
            String action, HttpServletRequest request, String identifier,
            int maxAttempts, long windowMinutes, long blockMinutes
    ) {
        record(key(action, request, identifier), maxAttempts, windowMinutes, blockMinutes);
        record(ipOnlyKey(action, request), maxAttempts * IP_ONLY_LIMIT_MULTIPLIER, windowMinutes, blockMinutes);
    }

    public void reset(String action, HttpServletRequest request, String identifier) {
        // Deliberately does NOT reset the IP-only bucket: it tracks the IP's own behavior across
        // every identifier it has tried, so one successful attempt under one identifier
        // shouldn't wipe out the record of others tried from the same IP moments earlier.
        attemptsByKey.remove(key(action, request, identifier));
    }

    private void checkKey(String key) {
        Attempt attempt = attemptsByKey.get(key);
        if (attempt != null && attempt.blockedUntil() != null && Instant.now().isBefore(attempt.blockedUntil())) {
            throw new TooManyAttemptsException("Too many attempts. Try again in a few minutes.");
        }
    }

    private void record(String key, int maxAttempts, long windowMinutes, long blockMinutes) {
        Instant now = Instant.now();
        attemptsByKey.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(Duration.ofMinutes(windowMinutes)))) {
                return new Attempt(1, now, null, now.plus(Duration.ofMinutes(Math.max(windowMinutes, blockMinutes))));
            }

            int count = existing.count() + 1;
            Instant blockedUntil = count >= maxAttempts ? now.plus(Duration.ofMinutes(blockMinutes)) : null;
            Instant staleAt = now.plus(Duration.ofMinutes(Math.max(windowMinutes, blockMinutes)));
            return new Attempt(count, existing.windowStart(), blockedUntil, staleAt);
        });
    }

    /**
     * Without this, a spoofed-IP flood (each request landing in a brand-new map key) would grow
     * attemptsByKey without bound - reset() only ever removes the one key of a successful login.
     * Runs independently of any single request, so abandoned entries (blocked/expired but never
     * retried) get reclaimed too, not just ones a client happens to hit again.
     */
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictStaleEntries() {
        Instant now = Instant.now();
        attemptsByKey.entrySet().removeIf(entry -> now.isAfter(entry.getValue().staleAt()));
    }

    private String key(String action, HttpServletRequest request, String identifier) {
        return action + "|" + resolveClientIp(request) + "|" + identifier;
    }

    private String ipOnlyKey(String action, HttpServletRequest request) {
        // IP_ONLY_IDENTIFIER can't collide with a real identifier value: every caller passes a
        // concrete email/phone/token, never the literal "*".
        return key(action, request, IP_ONLY_IDENTIFIER);
    }

    /**
     * The app is only reachable through Render's own edge/load balancer (the container has no
     * public IP of its own), so the nearest hop that appends to X-Forwarded-For is always
     * trustworthy infrastructure, not the client. Proxies APPEND to this header rather than
     * overwrite it, so a client-forged value ends up on the LEFT ("X-Forwarded-For: <attacker
     * supplied>, <real IP Render observed>") — reading the first entry (as this used to do) lets
     * anyone pick their own rate-limit bucket per request. Reading the LAST entry instead uses
     * the value Render itself appended, which the client cannot control.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
