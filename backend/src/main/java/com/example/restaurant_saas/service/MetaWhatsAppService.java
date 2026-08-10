package com.example.restaurant_saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends the order-ready notification through the WhatsApp Cloud API's Graph endpoint (no SDK
 * needed — a plain authenticated POST, same style as {@link BrevoEmailService}).
 *
 * <p>Note: the Cloud API only allows free-form text messages (as sent here) within the 24h
 * window after the customer last messaged the business number. Since customers here never
 * message the restaurant first, delivery isn't guaranteed until Meta's template-message flow is
 * wired in instead (requires a pre-approved message template per restaurant, configured in Meta
 * Business Manager) — out of scope until a restaurant actually needs this live.
 */
@Service
@ConditionalOnProperty(prefix = "app.whatsapp", name = "provider", havingValue = "meta")
public class MetaWhatsAppService implements WhatsAppService {

    private final RestClient restClient;
    private final String phoneNumberId;

    public MetaWhatsAppService(
            @Value("${whatsapp.access-token}") String accessToken,
            @Value("${whatsapp.phone-number-id}") String phoneNumberId
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com/v21.0")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        this.phoneNumberId = phoneNumberId;
    }

    @Override
    public void sendOrderReadyNotification(String toPhoneNumber, String restaurantName) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizePhone(toPhoneNumber),
                "type", "text",
                "text", Map.of("body", "Seu pedido na " + restaurantName + " está pronto! 🍽️")
        );

        restClient.post()
                .uri("/{phoneNumberId}/messages", phoneNumberId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // Package-private + static so it's unit-testable without spinning up the RestClient (same
    // pattern as BackupService#buildConnectionUri).
    static String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
}
