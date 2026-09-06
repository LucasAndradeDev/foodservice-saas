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
    @Operation(summary = "Quote a delivery fee", description = "Priority is distance (geocodes street/number/neighborhood/city via Nominatim, prices by the restaurant's base fee + price/km) when the restaurant has both a confirmed location and that pricing configured; falls back to the fixed neighborhood/DeliveryZone table (case-insensitive, exact match) otherwise. Always returns 200 - available=false means neither method could price this address, not an error. This is a preview only; the fee actually charged is looked up again server-side when the order is created.")
    public ResponseEntity<DeliveryFeeQuoteResponse> getFeeQuote(
            @PathVariable String slug,
            @RequestParam String street,
            @RequestParam String number,
            @RequestParam String neighborhood,
            @RequestParam String city,
            @RequestParam(required = false) String zipCode
    ) {
        return ResponseEntity.ok(publicDeliveryZoneService.getFeeQuote(slug, street, number, neighborhood, city, zipCode));
    }
}
