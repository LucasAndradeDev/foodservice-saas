package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AssignCourierRequest;
import com.example.restaurant_saas.dto.request.UpdateCourierLocationRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.example.restaurant_saas.dto.response.CourierLiveLocationResponse;
import com.example.restaurant_saas.dto.response.CourierOptionResponse;
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
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER','COURIER')")
    @Operation(summary = "Update delivery status", description = "Moves a delivery order to the next status in the flow, or cancels it. CANCELLED is a side-exit allowed from SEPARATING or OUT_FOR_DELIVERY (never from a terminal state), otherwise skipping a step or going backwards is rejected. A courier may only mark their own out-for-delivery order as delivered - they can't cancel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Delivery order not found in this restaurant, or invalid status transition"),
            @ApiResponse(responseCode = "403", description = "A courier tried to update an order not assigned to them, or a transition other than marking it delivered")
    })
    public ResponseEntity<DeliveryDetailsResponse> updateStatus(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID tabId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request
    ) {
        return ResponseEntity.ok(deliveryService.updateStatus(
                currentUser.getRestaurantId(), currentUser.getId(), extractRole(currentUser), currentUser.getName(), tabId, request));
    }

    @PatchMapping("/{tabId}/courier")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Assign or unassign a courier", description = "Sets the courier carrying a delivery order. Send courierId: null to unassign.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Courier assignment updated"),
            @ApiResponse(responseCode = "400", description = "Delivery order or courier not found in this restaurant")
    })
    public ResponseEntity<DeliveryDetailsResponse> assignCourier(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID tabId,
            @RequestBody AssignCourierRequest request
    ) {
        return ResponseEntity.ok(deliveryService.assignCourier(currentUser.getRestaurantId(), tabId, request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "List my deliveries", description = "For the authenticated courier: lists their own orders currently out for delivery.")
    public ResponseEntity<List<DeliveryDetailsResponse>> listMyDeliveries(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryService.listMyDeliveries(currentUser.getRestaurantId(), currentUser.getId()));
    }

    @GetMapping("/mine/history")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "List my deliveries completed today", description = "For the authenticated courier: their own orders marked DELIVERED today (restaurant's local day), most recent first. Backs the day summary card and recent-history list on their screen.")
    public ResponseEntity<List<DeliveryDetailsResponse>> listMyDeliveredToday(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryService.listMyDeliveredToday(currentUser.getRestaurantId(), currentUser.getId()));
    }

    @GetMapping("/couriers")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "List assignable couriers", description = "Lists the restaurant's courier accounts for the assignment dropdown - narrower than /api/v1/users so non-management roles don't get a courier's email back.")
    public ResponseEntity<List<CourierOptionResponse>> listAssignableCouriers(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryService.listAssignableCouriers(currentUser.getRestaurantId()));
    }

    @PatchMapping("/mine/location")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Report my location", description = "For the authenticated courier: updates their own last-known position. Sent periodically by the browser while /my-deliveries is open, whenever logged in - not gated on having an active delivery.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Location updated"),
            @ApiResponse(responseCode = "400", description = "Validation error (latitude/longitude out of range)")
    })
    public ResponseEntity<Void> updateMyLocation(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody UpdateCourierLocationRequest request
    ) {
        deliveryService.updateMyLocation(currentUser.getRestaurantId(), currentUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/couriers/live")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "List online couriers", description = "Lists the restaurant's couriers who have reported a position recently, with their exact coordinates - for the staff dispatch map. Unlike /couriers, this only includes couriers currently online, whether or not they're carrying a delivery.")
    public ResponseEntity<List<CourierLiveLocationResponse>> listLiveCouriers(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(deliveryService.listLiveCouriers(currentUser.getRestaurantId()));
    }

    private UserRole extractRole(UserDetailsImpl currentUser) {
        String authority = currentUser.getAuthorities().iterator().next().getAuthority();
        return UserRole.valueOf(authority.replace("ROLE_", ""));
    }
}
