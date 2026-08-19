package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.DeliveryDetailsResponse;
import com.example.restaurant_saas.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/deliveries")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicDeliveryStatusController {

    private final DeliveryService deliveryService;

    @GetMapping("/{token}/status")
    @Operation(summary = "Get a delivery order's status by its access token", description = "Returns the delivery order identified by the given access token, without authentication - the token itself is the only credential, there is no restaurant slug in this path. Meant to be polled while the order is in progress (docs/DELIVERY.md task 27.3), so unlike the reservation lookup it is not rate-limited: the UUID token already has negligible guess probability, and a normal polling cadence would otherwise trip a limit tuned for one-off lookups.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery order returned"),
            @ApiResponse(responseCode = "400", description = "No delivery order found for this token")
    })
    public ResponseEntity<DeliveryDetailsResponse> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(deliveryService.getByAccessToken(token));
    }
}
