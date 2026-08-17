package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.CardGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to the Mercado Pago REST API (no SDK, same style as {@link WooviApiClient}). Like Woovi,
 * there's no single shared credential: each call authenticates as a specific restaurant's own
 * Mercado Pago account, so the access token is passed per call instead of baked into the client
 * at construction time.
 *
 * <p>Mercado Pago's Authorization header is {@code Bearer <token>} (confirmed against
 * developers.mercadopago.com during design - unlike Woovi's raw-AppID quirk, this is the standard
 * OAuth bearer scheme). Response field names ({@code init_point}, {@code status_detail}, etc.)
 * are parsed defensively as loose maps, same reasoning as {@link WooviApiClient}: worth
 * re-checking against a real sandbox account before the first live charge.
 */
@Service
public class MercadoPagoApiClient {

    private final RestClient restClient;

    public MercadoPagoApiClient(@Value("${mercadopago.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public record PreferenceResult(String preferenceId, String initPoint) {
    }

    public record PaymentResult(
            String id, String status, String statusDetail, String externalReference,
            BigDecimal transactionAmount, String paymentTypeId
    ) {
    }

    /**
     * Creates a Checkout Pro preference (hosted checkout session) for the given amount.
     * {@code externalReference} is our own CardCharge.externalReference - Mercado Pago's
     * GET /v1/payments/{id} response echoes it back untouched, which is how the webhook's
     * authoritative fetch finds its way back to the right tenant/tab (see
     * card_charge_by_external_reference, V61). {@code notificationUrl} carries a
     * {restaurantId} path segment so the webhook can resolve the tenant before it even has a
     * token to call back with (Mercado Pago's webhook body itself carries no tenant info - just a
     * payment id).
     */
    public PreferenceResult createPreference(
            String accessToken, String externalReference, BigDecimal amount, String description,
            String notificationUrl, String successUrl, String pendingUrl, String failureUrl
    ) {
        Map<String, Object> item = Map.of(
                "title", description,
                "quantity", 1,
                "unit_price", amount,
                "currency_id", "BRL"
        );

        // No auto_return: Mercado Pago requires back_urls.success to be HTTPS for it to work at
        // all (rejects the whole preference with "auto_return invalid. back_url.success must be
        // defined" otherwise, confirmed against a real sandbox account during testing) - which
        // local dev's http://localhost front end can never satisfy. Not a real loss: the redirect
        // is only ever cosmetic feedback (see CardChargeService's security invariant), so the
        // customer/staff seeing a manual "voltar ao site" button on Mercado Pago's page instead of
        // an automatic redirect changes nothing about correctness or security.
        Map<String, Object> requestBody = Map.of(
                "items", List.of(item),
                "external_reference", externalReference,
                "notification_url", notificationUrl,
                "back_urls", Map.of(
                        "success", successUrl,
                        "pending", pendingUrl,
                        "failure", failureUrl
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new CardGatewayException("Mercado Pago returned an empty response creating the preference.");
            }

            String preferenceId = asString(response.get("id"));
            // sandbox_init_point is what test-mode access tokens actually return usable data in;
            // init_point is null/absent for those. Falling back covers both without needing to
            // know in advance which kind of token this restaurant configured.
            String initPoint = asString(response.get("init_point"));
            if (initPoint == null) {
                initPoint = asString(response.get("sandbox_init_point"));
            }

            if (initPoint == null) {
                throw new CardGatewayException("Mercado Pago's response did not include an init_point. Raw response: " + response);
            }

            return new PreferenceResult(preferenceId, initPoint);
        } catch (RestClientException e) {
            throw new CardGatewayException("Failed to create Mercado Pago preference: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a payment's authoritative status directly from Mercado Pago - the webhook
     * notification body itself only ever carries a payment id, never the status or
     * external_reference, by design on Mercado Pago's side. This call, not the webhook body, is
     * the source of truth {@link com.example.restaurant_saas.service.CardChargeService#handleWebhook}
     * acts on.
     */
    public PaymentResult getPayment(String accessToken, String paymentId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new CardGatewayException("Mercado Pago returned an empty response fetching payment " + paymentId);
            }

            return toPaymentResult(response);
        } catch (RestClientException e) {
            throw new CardGatewayException("Failed to fetch Mercado Pago payment " + paymentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Alternative way to find a payment's authoritative status, keyed by our own
     * {@code external_reference} instead of Mercado Pago's payment id - used by
     * {@link com.example.restaurant_saas.service.CardChargeService#verifyPendingChargeByExternalReference}
     * when the browser lands back on our own {@code /pagamento/retorno} redirect after checkout,
     * as a second path to the same authoritative source alongside the webhook (see
     * docs/CARD_PAYMENT.md - the webhook's signature has shown sandbox-only flakiness for
     * {@code live_mode: true} payments). Safe precisely because it's just another way of asking
     * Mercado Pago itself, scoped to this restaurant's own access token - never trusts anything
     * the browser's redirect URL claims about payment status. Returns {@code null} if no payment
     * has been created against that external_reference yet (customer hasn't finished paying, or
     * never will).
     */
    public PaymentResult searchPaymentByExternalReference(String accessToken, String externalReference) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/payments/search")
                            .queryParam("external_reference", externalReference)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                return null;
            }

            return toPaymentResult(results.get(0));
        } catch (RestClientException e) {
            throw new CardGatewayException(
                    "Failed to search Mercado Pago payments for external_reference " + externalReference + ": " + e.getMessage(), e);
        }
    }

    private PaymentResult toPaymentResult(Map<String, Object> payment) {
        Object rawAmount = payment.get("transaction_amount");
        BigDecimal amount = rawAmount != null ? new BigDecimal(rawAmount.toString()) : null;

        return new PaymentResult(
                asString(payment.get("id")),
                asString(payment.get("status")),
                asString(payment.get("status_detail")),
                asString(payment.get("external_reference")),
                amount,
                asString(payment.get("payment_type_id"))
        );
    }

    /**
     * Issues a full refund (no {@code amount} in the body - partial refunds are out of scope for
     * V1, see docs/CARD_PAYMENT.md). {@code X-Idempotency-Key} is a fresh UUID per call so a retry
     * of this exact call (e.g. a timeout where the first attempt actually succeeded server-side)
     * can't accidentally issue two refunds.
     */
    public String refundPayment(String accessToken, String paymentId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/v1/payments/{id}/refunds", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new CardGatewayException("Mercado Pago returned an empty response refunding payment " + paymentId);
            }

            return asString(response.get("id"));
        } catch (RestClientException e) {
            throw new CardGatewayException("Failed to refund Mercado Pago payment " + paymentId + ": " + e.getMessage(), e);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
