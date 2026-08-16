package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateDeliveryOrderRequest;
import com.example.restaurant_saas.dto.response.DeliveryOrderResponse;
import com.example.restaurant_saas.service.PublicDeliveryOrderService;
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

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/delivery/orders")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicDeliveryOrderController {

    private final PublicDeliveryOrderService publicDeliveryOrderService;

    @PostMapping
    @Operation(summary = "Submit a delivery order", description = "Opens a new table-less tab (same shape as Balcao) with the given items and delivery address, without authentication. Always opens a new tab - unlike table self-ordering, there's no existing open tab to add to. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delivery order created"),
            @ApiResponse(responseCode = "400", description = "Validation error, or restaurant/product not found"),
            @ApiResponse(responseCode = "403", description = "A product is inactive or unavailable"),
            @ApiResponse(responseCode = "429", description = "Too many attempts with this phone number")
    })
    public ResponseEntity<DeliveryOrderResponse> createDeliveryOrder(
            @PathVariable String slug,
            @Valid @RequestBody CreateDeliveryOrderRequest request
    ) {
        DeliveryOrderResponse response = publicDeliveryOrderService.createDeliveryOrder(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
