package com.example.warehouse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateIngredientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @NotBlank(message = "Unit is required")
    @Size(max = 20, message = "Unit must be at most 20 characters long")
    private String unit;

    @DecimalMin(value = "0", message = "Current quantity cannot be negative")
    private BigDecimal currentQuantity;

    @DecimalMin(value = "0", message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;
}
