package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.WarehouseProductResponse;
import com.example.restaurant_saas.service.WarehouseIntegrationService;
import com.example.restaurant_saas.service.WarehouseSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Not user-facing: called by Armazém Morá when the owner builds a ficha técnica
 * (docs/ARMAZEM_MORA.md), so the product picker shows real Morá products instead of a pasted-in
 * UUID. Same X-Warehouse-Api-Key auth as WarehouseSalesController.
 */
@RestController
@RequestMapping("/api/v1/internal/warehouse/products")
@RequiredArgsConstructor
@Tag(name = "Warehouse Products (internal)", description = "Active simple products for Armazém Morá's recipe picker. Not part of the public API surface.")
public class WarehouseProductsController {

    private static final String API_KEY_HEADER = "X-Warehouse-Api-Key";

    private final WarehouseIntegrationService warehouseIntegrationService;
    private final WarehouseSalesService warehouseSalesService;

    @GetMapping
    @Operation(summary = "List active simple products", description = "Returns active, non-combo products for the restaurant identified by the API key header - combos are excluded since sales are reported with their components already exploded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Warehouse-Api-Key header")
    })
    public ResponseEntity<List<WarehouseProductResponse>> listProducts(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey
    ) {
        UUID restaurantId = warehouseIntegrationService.resolveRestaurantId(apiKey).orElse(null);
        if (restaurantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(warehouseSalesService.findActiveSimpleProducts(restaurantId));
    }
}
