package com.example.warehouse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreatePurchaseItemRequest {

    @NotNull(message = "Ingredient is required")
    private UUID ingredientId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    @DecimalMin(value = "0", message = "Unit cost cannot be negative")
    private BigDecimal unitCost;
}
