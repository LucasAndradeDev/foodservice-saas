package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.service.CardChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/payments/mercadopago/webhook/{restaurantId}")
@RequiredArgsConstructor
@Tag(name = "Public Payments Webhook", description = "Mercado Pago calls this to notify about a payment. No authentication - the {restaurantId} path segment (embedded in the notification_url each preference is created with) resolves the tenant, and the x-signature header (HMAC-SHA256 against that restaurant's own webhook secret) verifies authenticity. The notification body itself is never trusted for payment status - see CardChargeService#handleWebhook.")
public class PublicMercadoPagoWebhookController {

    private final CardChargeService cardChargeService;

    // Reads the raw body directly instead of a @RequestBody-deserialized object: signature
    // verification must run over the exact bytes Mercado Pago sent, before any JSON
    // parsing/reserialization could change so much as a single byte of whitespace.
    @PostMapping
    @Operation(summary = "Receive a Mercado Pago webhook call")
    public ResponseEntity<Void> receiveWebhook(
            HttpServletRequest request,
            @PathVariable UUID restaurantId,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId
    ) {
        byte[] rawBody = readRawBody(request);
        cardChargeService.handleWebhook(restaurantId, rawBody, signature, requestId);
        return ResponseEntity.ok().build();
    }

    private byte[] readRawBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
