package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.service.PublicOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/tables/{tableId}/orders")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicOrderController {

    private final PublicOrderService publicOrderService;

    @PostMapping
    @Operation(summary = "Submit a self-order for a table", description = "Creates a new order with its items for the given table, without authentication. If the table has no open tab, one is opened automatically (the table must be active and FREE). If it already has an open tab, the order is added to it. Goes straight to the kitchen, same as a staff-created order. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Validation error, or restaurant/table/product not found"),
            @ApiResponse(responseCode = "403", description = "Table is inactive or not available for self-ordering, or a product is inactive")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @PathVariable String slug,
            @PathVariable UUID tableId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = publicOrderService.createOrder(slug, tableId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
