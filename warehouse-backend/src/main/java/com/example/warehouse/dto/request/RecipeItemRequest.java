package com.example.warehouse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecipeItemRequest {

    @NotNull(message = "Ingredient is required")
    private UUID ingredientId;

    @NotNull(message = "Quantity per unit is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity per unit must be greater than zero")
    private BigDecimal quantityPerUnit;
}
