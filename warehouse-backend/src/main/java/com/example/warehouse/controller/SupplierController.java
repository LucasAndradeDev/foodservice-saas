package com.example.warehouse.controller;

import com.example.warehouse.dto.request.CreateSupplierRequest;
import com.example.warehouse.dto.request.UpdateSupplierRequest;
import com.example.warehouse.dto.response.SupplierResponse;
import com.example.warehouse.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Suppliers used to record purchases.")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "List suppliers", description = "Lists this restaurant's suppliers, optionally filtered by active status.")
    public ResponseEntity<List<SupplierResponse>> listSuppliers(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(supplierService.listSuppliers(restaurantLinkId, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get supplier", description = "Returns a single supplier by id, scoped to the authenticated restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier returned"),
            @ApiResponse(responseCode = "400", description = "Supplier not found in this restaurant")
    })
    public ResponseEntity<SupplierResponse> getSupplier(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(supplierService.getSupplier(restaurantLinkId, id));
    }

    @PostMapping
    @Operation(summary = "Create supplier", description = "Creates a new supplier. Name must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Supplier created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate name")
    })
    public ResponseEntity<SupplierResponse> createSupplier(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Valid @RequestBody CreateSupplierRequest request
    ) {
        SupplierResponse response = supplierService.createSupplier(restaurantLinkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update supplier", description = "Updates name, contact and/or active status. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier updated"),
            @ApiResponse(responseCode = "400", description = "Supplier not found, validation error, or duplicate name")
    })
    public ResponseEntity<SupplierResponse> updateSupplier(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return ResponseEntity.ok(supplierService.updateSupplier(restaurantLinkId, id, request));
    }
}
