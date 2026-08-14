package com.example.warehouse.controller;

import com.example.warehouse.dto.request.CreatePurchaseRequest;
import com.example.warehouse.dto.response.PurchaseResponse;
import com.example.warehouse.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
@Tag(name = "Purchases", description = "Purchase records - each one adds its items' quantities onto ingredient stock.")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    @Operation(summary = "List purchases", description = "Lists this restaurant's purchases, most recent first.")
    public ResponseEntity<List<PurchaseResponse>> listPurchases(@AuthenticationPrincipal UUID restaurantLinkId) {
        return ResponseEntity.ok(purchaseService.listPurchases(restaurantLinkId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get purchase", description = "Returns a single purchase by id, scoped to the authenticated restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase returned"),
            @ApiResponse(responseCode = "400", description = "Purchase not found in this restaurant")
    })
    public ResponseEntity<PurchaseResponse> getPurchase(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(purchaseService.getPurchase(restaurantLinkId, id));
    }

    @PostMapping
    @Operation(summary = "Create purchase", description = "Records a purchase and adds each item's quantity onto the matching ingredient's current stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase recorded and stock updated"),
            @ApiResponse(responseCode = "400", description = "Validation error, or supplier/ingredient not found in this restaurant")
    })
    public ResponseEntity<PurchaseResponse> createPurchase(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Valid @RequestBody CreatePurchaseRequest request
    ) {
        PurchaseResponse response = purchaseService.createPurchase(restaurantLinkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
