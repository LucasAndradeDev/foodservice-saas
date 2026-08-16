package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.DeliveryFeeQuoteResponse;
import com.example.restaurant_saas.service.PublicDeliveryZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/delivery")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicDeliveryZoneController {

    private final PublicDeliveryZoneService publicDeliveryZoneService;

    @GetMapping("/fee")
    @Operation(summary = "Quote a delivery fee", description = "Looks up the fixed fee for a neighborhood (case-insensitive, exact match, no geocoding). Always returns 200 - available=false means the neighborhood isn't served, not an error. This is a preview only; the fee actually charged is looked up again server-side when the order is created.")
    public ResponseEntity<DeliveryFeeQuoteResponse> getFeeQuote(
            @PathVariable String slug,
            @RequestParam String neighborhood
    ) {
        return ResponseEntity.ok(publicDeliveryZoneService.getFeeQuote(slug, neighborhood));
    }
}
