package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenCashRegisterRequest {

    @NotNull(message = "Opening amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Opening amount cannot be negative")
    private BigDecimal openingAmount;
}
