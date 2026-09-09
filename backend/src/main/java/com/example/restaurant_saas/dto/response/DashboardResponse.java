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
    // Lets the frontend's getting-started card dismiss itself only once the menu is actually set
    // up, instead of the table count alone (2026-09-09 onboarding audit, finding #1) - a restaurant
    // that sets up its dining room before its menu was losing the "Importar cardápio" prompt the
    // moment the first table existed, with no menu yet.
    private boolean hasProducts;
}
