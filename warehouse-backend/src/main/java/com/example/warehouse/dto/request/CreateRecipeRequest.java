package com.example.warehouse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRecipeRequest {

    @NotNull(message = "Morá product is required")
    private UUID moraProductId;

    @NotBlank(message = "Morá product name is required")
    @Size(max = 150, message = "Morá product name must be at most 150 characters long")
    private String moraProductName;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<RecipeItemRequest> items;
}
