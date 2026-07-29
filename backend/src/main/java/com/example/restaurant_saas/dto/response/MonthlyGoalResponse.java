package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MonthlyGoalResponse {
    private LocalDate month;
    private BigDecimal revenueGoal;
    private BigDecimal currentRevenue;
    private BigDecimal progressPercentage;
}
