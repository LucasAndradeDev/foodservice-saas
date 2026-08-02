package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.OrderService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Manage orders (kitchen submissions) sent under a tab. Each order is created already complete with its items. Write operations restricted to OWNER, MANAGER, WAITER and CASHIER.")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/tabs/{tabId}/orders")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Create order", description = "Creates a new order with its items under an open tab. All products must exist in the restaurant and be active; unit price is snapshotted from the product's current price. The tab must be OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Validation error, or tab/product not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, tab is not OPEN, or a product is inactive")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID tabId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.createOrder(currentUser.getRestaurantId(), tabId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tabs/{tabId}/orders")
    @Operation(summary = "List orders for a tab", description = "Lists all orders created under the given tab.")
    public ResponseEntity<List<OrderResponse>> listOrders(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID tabId
    ) {
        return ResponseEntity.ok(orderService.listOrders(currentUser.getRestaurantId(), tabId));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get order", description = "Returns a single order by id, including its items and total, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order returned"),
            @ApiResponse(responseCode = "400", description = "Order not found in this restaurant")
    })
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(orderService.getOrder(currentUser.getRestaurantId(), id));
    }

    @PatchMapping("/orders/{id}/print")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Mark order as printed", description = "Records that this order's kitchen ticket was printed by setting printedAt to now. Can be called again on reprint (idempotent, always overwrites with the latest timestamp).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked as printed"),
            @ApiResponse(responseCode = "400", description = "Order not found in this restaurant")
    })
    public ResponseEntity<OrderResponse> markPrinted(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(orderService.markPrinted(currentUser.getRestaurantId(), id));
    }
}
