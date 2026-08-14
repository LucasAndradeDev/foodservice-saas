package com.example.warehouse.controller;

import com.example.warehouse.dto.request.CreateIngredientRequest;
import com.example.warehouse.dto.request.UpdateIngredientRequest;
import com.example.warehouse.dto.response.IngredientResponse;
import com.example.warehouse.service.IngredientService;
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
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
@Tag(name = "Ingredients", description = "Ingredient catalog and current stock balance.")
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    @Operation(summary = "List ingredients", description = "Lists this restaurant's ingredients, optionally filtered by active status.")
    public ResponseEntity<List<IngredientResponse>> listIngredients(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ingredientService.listIngredients(restaurantLinkId, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ingredient", description = "Returns a single ingredient by id, scoped to the authenticated restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingredient returned"),
            @ApiResponse(responseCode = "400", description = "Ingredient not found in this restaurant")
    })
    public ResponseEntity<IngredientResponse> getIngredient(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ingredientService.getIngredient(restaurantLinkId, id));
    }

    @PostMapping
    @Operation(summary = "Create ingredient", description = "Creates a new ingredient. Name must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ingredient created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate name")
    })
    public ResponseEntity<IngredientResponse> createIngredient(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Valid @RequestBody CreateIngredientRequest request
    ) {
        IngredientResponse response = ingredientService.createIngredient(restaurantLinkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ingredient", description = "Updates name, unit, current quantity, low stock threshold and/or active status. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingredient updated"),
            @ApiResponse(responseCode = "400", description = "Ingredient not found, validation error, or duplicate name")
    })
    public ResponseEntity<IngredientResponse> updateIngredient(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIngredientRequest request
    ) {
        return ResponseEntity.ok(ingredientService.updateIngredient(restaurantLinkId, id, request));
    }
}
