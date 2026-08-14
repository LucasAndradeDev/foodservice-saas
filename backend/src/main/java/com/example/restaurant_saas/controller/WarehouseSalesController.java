package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.WarehouseSaleItemResponse;
import com.example.restaurant_saas.service.WarehouseIntegrationService;
import com.example.restaurant_saas.service.WarehouseSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Not user-facing: called by Armazém Morá's sync job (see docs/ARMAZEM_MORA.md), not by a
 * logged-in user. Authenticated by the integration API key header instead of the normal JWT flow
 * - see SecurityConfig, which permits the whole /api/v1/internal/** path through Spring Security's
 * authorization so this check can happen here, same shape as BackupController.
 */
@RestController
@RequestMapping("/api/v1/internal/warehouse/sales")
@RequiredArgsConstructor
@Tag(name = "Warehouse Sales (internal)", description = "Delivered order items for Armazém Morá's stock sync. Not part of the public API surface.")
public class WarehouseSalesController {

    private static final String API_KEY_HEADER = "X-Warehouse-Api-Key";

    private final WarehouseIntegrationService warehouseIntegrationService;
    private final WarehouseSalesService warehouseSalesService;

    @GetMapping
    @Operation(summary = "List delivered sales since a timestamp", description = "Returns delivered order items (combo components already exploded) for the restaurant identified by the API key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sales returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Warehouse-Api-Key header")
    })
    public ResponseEntity<List<WarehouseSaleItemResponse>> listSales(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Parameter(description = "Only items delivered after this instant") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since
    ) {
        UUID restaurantId = warehouseIntegrationService.resolveRestaurantId(apiKey).orElse(null);
        if (restaurantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(warehouseSalesService.findDeliveredSalesSince(restaurantId, since));
    }
}
