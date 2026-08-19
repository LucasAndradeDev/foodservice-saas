package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.example.restaurant_saas.dto.response.DeliveryDetailsResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Tag(name = "Deliveries", description = "Delivery status operation (docs/DELIVERY.md, task 27). Status moves forward only: SEPARATING -> OUT_FOR_DELIVERY -> DELIVERED.")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    @Operation(summary = "List open deliveries", description = "Lists the restaurant's delivery orders not yet DELIVERED, ordered by creation time (oldest first).")
    public ResponseEntity<List<DeliveryDetailsResponse>> listOpenDeliveries(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryService.listOpenDeliveries(currentUser.getRestaurantId()));
    }

    @PatchMapping("/{tabId}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Update delivery status", description = "Moves a delivery order to the next status in the flow. Skipping a step or going backwards is rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Delivery order not found in this restaurant, or invalid status transition")
    })
    public ResponseEntity<DeliveryDetailsResponse> updateStatus(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID tabId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request
    ) {
        return ResponseEntity.ok(deliveryService.updateStatus(currentUser.getRestaurantId(), tabId, request));
    }
}
