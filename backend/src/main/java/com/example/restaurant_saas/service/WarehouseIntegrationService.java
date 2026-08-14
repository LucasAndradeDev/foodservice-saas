package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.WarehouseIntegration;
import com.example.restaurant_saas.repository.WarehouseIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseIntegrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WarehouseIntegrationRepository warehouseIntegrationRepository;

    /**
     * Mints a fresh, long-lived API key for Armazém Morá's background sync job to call
     * {@code GET /api/v1/internal/warehouse/sales} with, and stores only its hash. The raw
     * value is returned once, embedded in the short-lived SSO handoff token (see
     * WarehouseHandoffTokenService) - never persisted here in the clear, never shown to the
     * user. Every handoff mints a new key, overwriting the stored hash: re-pairing IS how the
     * key rotates, so there's no separate rotation flow to build for v1.
     */
    @Transactional
    public String rotateApiKey(UUID restaurantId) {
        String rawKey = generateRawKey();
        String hash = hash(rawKey);

        WarehouseIntegration integration = warehouseIntegrationRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> WarehouseIntegration.builder().restaurantId(restaurantId).build());
        integration.setApiKeyHash(hash);
        warehouseIntegrationRepository.save(integration);

        return rawKey;
    }

    /**
     * Resolves which restaurant a raw integration API key belongs to - used to authenticate
     * Armazém Morá's sync job (see WarehouseSalesController), which has no JWT/tenant to present.
     * Goes through {@code findByApiKeyHashBypassingRls} deliberately: RLS blocks this table with
     * no tenant set, and the whole point here is finding out which tenant it is.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolveRestaurantId(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        return warehouseIntegrationRepository.findByApiKeyHashBypassingRls(hash(rawApiKey))
                .map(WarehouseIntegration::getRestaurantId);
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
