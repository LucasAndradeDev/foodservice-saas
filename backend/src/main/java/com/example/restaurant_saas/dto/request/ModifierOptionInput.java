package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModifierOptionInput {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @NotNull(message = "Price delta is required")
    @DecimalMin(value = "0.00", message = "Price delta must not be negative")
    private BigDecimal priceDelta;
}
