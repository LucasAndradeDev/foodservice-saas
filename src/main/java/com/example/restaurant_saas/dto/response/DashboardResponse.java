package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardResponse {
    private long freeTables;
    private long occupiedTables;
    private long ordersInPreparation;
    private BigDecimal revenueToday;
}
