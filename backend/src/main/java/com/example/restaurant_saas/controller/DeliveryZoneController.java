package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.response.DeliveryZoneResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.DeliveryZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-zones")
@RequiredArgsConstructor
@Tag(name = "Delivery Zones", description = "Neighborhood -> fixed delivery fee list (docs/DELIVERY.md, no geocoding in v1). Write operations restricted to OWNER and MANAGER.")
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;

    @GetMapping
    @Operation(summary = "List delivery zones", description = "Lists the restaurant's delivery zones ordered by neighborhood, including inactive ones.")
    public ResponseEntity<List<DeliveryZoneResponse>> listZones(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryZoneService.listZones(currentUser.getRestaurantId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create delivery zone", description = "Creates a new delivery zone. Neighborhood must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delivery zone created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate neighborhood"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<DeliveryZoneResponse> createZone(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateDeliveryZoneRequest request
    ) {
        DeliveryZoneResponse response = deliveryZoneService.createZone(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update delivery zone", description = "Updates a delivery zone's neighborhood, fee and active flag. Neighborhood must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery zone updated"),
            @ApiResponse(responseCode = "400", description = "Delivery zone not found, validation error, or duplicate neighborhood"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<DeliveryZoneResponse> updateZone(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeliveryZoneRequest request
    ) {
        return ResponseEntity.ok(deliveryZoneService.updateZone(currentUser.getRestaurantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete delivery zone", description = "Permanently deletes a delivery zone.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Delivery zone deleted"),
            @ApiResponse(responseCode = "400", description = "Delivery zone not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<Void> deleteZone(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        deliveryZoneService.deleteZone(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }
}
