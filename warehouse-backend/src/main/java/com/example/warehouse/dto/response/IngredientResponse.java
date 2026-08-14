package com.example.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class IngredientResponse {
    private UUID id;
    private String name;
    private String unit;
    private BigDecimal currentQuantity;
    private BigDecimal lowStockThreshold;
    private Boolean active;
    // Computed, not stored - true when a threshold is set and the current balance has dropped
    // to or below it.
    private boolean lowStock;
}
