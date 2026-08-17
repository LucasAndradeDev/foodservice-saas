package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.service.CardChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/payments/mercadopago/verify/{externalReference}")
@RequiredArgsConstructor
@Tag(name = "Public Payments Webhook", description = "Second, browser-triggered path to the same authoritative Mercado Pago lookup the webhook uses - see CardChargeService#verifyPendingChargeByExternalReference for why this stays safe without trusting anything the request itself claims.")
public class PublicCardChargeVerificationController {

    private final CardChargeService cardChargeService;

    @PostMapping
    @Operation(
            summary = "Re-check a pending card charge directly against Mercado Pago",
            description = "Called by CardPaymentReturnPage the moment the paying browser lands back on our redirect after "
                    + "Checkout Pro, as a backstop for the async webhook. A safe no-op if the charge is missing, already "
                    + "resolved, or Mercado Pago doesn't have an approved/rejected payment for it yet."
    )
    public ResponseEntity<Void> verify(@PathVariable String externalReference) {
        cardChargeService.verifyPendingChargeByExternalReference(externalReference);
        return ResponseEntity.ok().build();
    }
}
