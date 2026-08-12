package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.PixGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Talks to the Woovi REST API (no SDK, same style as {@link BrevoEmailService}/
 * {@link SupabaseFileStorageService}). Unlike those, there's no single shared credential: each
 * call authenticates as a specific restaurant's own Woovi account, so the AppID is passed per
 * call instead of baked into the client at construction time.
 *
 * <p>Woovi's Authorization header takes the raw AppID directly - no "Bearer " prefix (confirmed
 * against developers.woovi.com during design; worth re-checking against a real sandbox account
 * before the first live charge, since this integration hasn't been exercised against Woovi yet).
 */
@Service
public class WooviApiClient {

    private final RestClient restClient;

    public WooviApiClient(@Value("${woovi.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public record ChargeResult(String brCode, String qrCodeImage, String paymentLinkUrl) {
    }

    /**
     * Creates a Pix charge ("cob") for the given amount. {@code correlationId} is our own
     * PixCharge.externalChargeId - Woovi echoes it back untouched in the webhook payload, which is
     * how the webhook finds its way back to the right tenant (see pix_charge_by_external_id, V58).
     */
    public ChargeResult createCharge(String appId, String correlationId, BigDecimal amount, String comment) {
        long valueInCents = amount.movePointRight(2).longValueExact();

        Map<String, Object> requestBody = Map.of(
                "correlationID", correlationId,
                "value", valueInCents,
                "comment", comment
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/charge")
                    .header("Authorization", appId)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new PixGatewayException("Woovi returned an empty response creating the charge.");
            }

            String brCode = firstNonNull(response, "brCode");
            String qrCodeImage = firstNonNull(response, "qrCodeImage");
            String paymentLinkUrl = firstNonNull(response, "paymentLinkUrl");

            if (brCode == null) {
                throw new PixGatewayException("Woovi's response did not include a brCode. Raw response: " + response);
            }

            return new ChargeResult(brCode, qrCodeImage, paymentLinkUrl);
        } catch (RestClientException e) {
            throw new PixGatewayException("Failed to create Pix charge with Woovi: " + e.getMessage(), e);
        }
    }

    private String firstNonNull(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null && response.get("charge") instanceof Map<?, ?> nestedCharge) {
            value = nestedCharge.get(key);
        }
        return value != null ? value.toString() : null;
    }
}
