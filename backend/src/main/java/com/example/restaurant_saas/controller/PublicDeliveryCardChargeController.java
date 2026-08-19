package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.CardChargeResponse;
import com.example.restaurant_saas.service.CardChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/deliveries/{token}/card-charges")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicDeliveryCardChargeController {

    private final CardChargeService cardChargeService;

    @PostMapping
    @Operation(summary = "Create a card charge for a delivery order, started by the customer", description = "Same freeze-and-generate-checkout-link flow as the staff Caixa endpoint, but for a delivery order (task 29.1) identified by its access token instead of a table id. Unlike the dine-in flow, there is no DELIVERED-item gate: a delivery order is paid immediately at submission, before the kitchen starts. Requires the restaurant to have configured Mercado Pago. Confirmed asynchronously by a webhook.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Charge created, returns the Mercado Pago checkout link"),
            @ApiResponse(responseCode = "400", description = "Delivery order not found, or nothing left to charge"),
            @ApiResponse(responseCode = "502", description = "Mercado Pago could not be reached or returned an unexpected response")
    })
    public ResponseEntity<CardChargeResponse> createCharge(@PathVariable String token) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cardChargeService.createChargeForDeliveryOrder(token));
    }

    @DeleteMapping
    @Operation(summary = "Cancel this delivery order's pending card charge", description = "Lets the customer back out of their own still-PENDING card charge (e.g. to try Pix instead, or a stuck checkout link) and unfreezes the total if nothing else relies on it. A no-op if there's nothing PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cancelled (or nothing to cancel)"),
            @ApiResponse(responseCode = "400", description = "Delivery order not found")
    })
    public ResponseEntity<Void> cancelCharge(@PathVariable String token) {
        cardChargeService.cancelPendingChargeForDeliveryOrder(token);
        return ResponseEntity.noContent().build();
    }
}
