package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.TransferItemsRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.response.KitchenItemResponse;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.OrderItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/order-items")
@RequiredArgsConstructor
@Tag(name = "Order Items", description = "Kitchen queue and status updates for order items. Write operations restricted to OWNER, MANAGER, WAITER, KITCHEN and CASHIER, gated per status transition.")
public class OrderItemController {

    private final OrderItemService orderItemService;

    @GetMapping
    @Operation(summary = "List kitchen queue", description = "Lists order items across all tabs of the restaurant, filtered by status. Without a filter, defaults to items not yet finished (PENDING, PREPARING, READY), ordered by creation time.")
    public ResponseEntity<List<KitchenItemResponse>> listKitchenQueue(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by one or more statuses (comma-separated)") @RequestParam(required = false) List<ItemStatus> status
    ) {
        return ResponseEntity.ok(orderItemService.listKitchenQueue(currentUser.getRestaurantId(), status));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Update order item status", description = "Moves an order item to the next status in the flow (PENDING -> PREPARING -> READY -> DELIVERED, or CANCELLED from any non-final status). KITCHEN drives PENDING/PREPARING/READY; WAITER/CASHIER marks DELIVERED; any of them can CANCEL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Item not found in this restaurant, or the requested transition is not a valid next step"),
            @ApiResponse(responseCode = "403", description = "Authenticated user's role is not allowed to perform this specific transition")
    })
    public ResponseEntity<OrderItemResponse> updateStatus(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderItemStatusRequest request
    ) {
        OrderItemResponse response = orderItemService.updateStatus(currentUser.getRestaurantId(), id, currentUser.getRole(), request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/discount")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Apply or clear a one-off discount on an order item", description = "Sets a fixed-amount or percentage discount on this item, capped at its subtotal. Send discountType as null to clear an existing discount. Restricted to OWNER and MANAGER; only allowed while the item is not CANCELLED and its tab is still OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount applied or cleared"),
            @ApiResponse(responseCode = "400", description = "Item not found in this restaurant, item is cancelled, tab is not open, or discount value is invalid"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<OrderItemResponse> applyDiscount(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody ApplyDiscountRequest request
    ) {
        OrderItemResponse response = orderItemService.applyDiscount(currentUser.getRestaurantId(), id, currentUser.getName(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Transfer items to another open tab", description = "Moves one or more order items (any status except CANCELLED) out of their current tab into a different open tab, grouped into a new order there. The item's price, discount, status and original order time are preserved; only which tab/order it belongs to changes. The source order is never deleted, even if it ends up empty. Both tabs must be OPEN, and the target must be a different tab.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Items transferred"),
            @ApiResponse(responseCode = "400", description = "An item or the target tab was not found in this restaurant, items span more than one tab, an item is cancelled, either tab is not open, or source and target are the same tab"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<List<OrderItemResponse>> transferItems(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody TransferItemsRequest request
    ) {
        List<OrderItemResponse> response = orderItemService.transferItems(currentUser.getRestaurantId(), currentUser.getName(), request);
        return ResponseEntity.ok(response);
    }
}
