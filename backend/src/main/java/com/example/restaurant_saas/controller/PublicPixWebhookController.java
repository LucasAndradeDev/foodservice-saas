package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.service.PixChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/v1/public/payments/webhook")
@RequiredArgsConstructor
@Tag(name = "Public Payments Webhook", description = "Woovi calls this to confirm a Pix charge was paid. No authentication - verified instead by the x-webhook-signature header (RSA/SHA-256 against Woovi's public key).")
public class PublicPixWebhookController {

    private final PixChargeService pixChargeService;

    // Reads the raw body directly instead of a @RequestBody-deserialized object: signature
    // verification must run over the exact bytes Woovi sent, before any JSON parsing/reserialization
    // could change so much as a single byte of whitespace.
    @PostMapping
    @Operation(summary = "Receive a Woovi webhook call")
    public ResponseEntity<Void> receiveWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature
    ) {
        byte[] rawBody = readRawBody(request);
        pixChargeService.handleWebhook(rawBody, signature);
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
