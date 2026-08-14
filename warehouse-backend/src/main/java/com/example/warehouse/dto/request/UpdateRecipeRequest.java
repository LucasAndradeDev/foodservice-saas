package com.example.warehouse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * All fields optional - same partial-update convention as UpdateIngredientRequest. When items is
 * present, it replaces the recipe's entire item list rather than merging with it (a ficha
 * técnica is edited as a whole, not item-by-item).
 */
@Data
public class UpdateRecipeRequest {

    @Size(max = 150, message = "Morá product name must be at most 150 characters long")
    private String moraProductName;

    @Valid
    private List<RecipeItemRequest> items;
}
