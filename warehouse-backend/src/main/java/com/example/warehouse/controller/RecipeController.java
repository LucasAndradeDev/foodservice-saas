package com.example.warehouse.controller;

import com.example.warehouse.dto.request.CreateRecipeRequest;
import com.example.warehouse.dto.request.UpdateRecipeRequest;
import com.example.warehouse.dto.response.RecipeResponse;
import com.example.warehouse.service.RecipeService;
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
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipes", description = "Ficha técnica: maps a Morá product to the ingredients it consumes per unit sold.")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    @Operation(summary = "List recipes", description = "Lists this restaurant's recipes.")
    public ResponseEntity<List<RecipeResponse>> listRecipes(@AuthenticationPrincipal UUID restaurantLinkId) {
        return ResponseEntity.ok(recipeService.listRecipes(restaurantLinkId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get recipe", description = "Returns a single recipe by id, scoped to the authenticated restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe returned"),
            @ApiResponse(responseCode = "400", description = "Recipe not found in this restaurant")
    })
    public ResponseEntity<RecipeResponse> getRecipe(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(recipeService.getRecipe(restaurantLinkId, id));
    }

    @PostMapping
    @Operation(summary = "Create recipe", description = "Creates a recipe for a Morá product. Only one recipe per product is allowed per restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recipe created"),
            @ApiResponse(responseCode = "400", description = "Validation error, duplicate product, or an ingredient not found in this restaurant")
    })
    public ResponseEntity<RecipeResponse> createRecipe(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @Valid @RequestBody CreateRecipeRequest request
    ) {
        RecipeResponse response = recipeService.createRecipe(restaurantLinkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update recipe", description = "Updates the product name snapshot and/or replaces the recipe's entire item list. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe updated"),
            @ApiResponse(responseCode = "400", description = "Recipe not found, validation error, or an ingredient not found in this restaurant")
    })
    public ResponseEntity<RecipeResponse> updateRecipe(
            @AuthenticationPrincipal UUID restaurantLinkId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecipeRequest request
    ) {
        return ResponseEntity.ok(recipeService.updateRecipe(restaurantLinkId, id, request));
    }
}
