package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SetMonthlyGoalRequest {

    @NotNull(message = "Month is required")
    private LocalDate month;

    @NotNull(message = "Revenue goal is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Revenue goal must be greater than zero")
    private BigDecimal revenueGoal;
}
