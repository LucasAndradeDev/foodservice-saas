package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReportComparisonResponse {
    private BigDecimal previousTotalRevenue;
    private long previousClosedTabsCount;
    private BigDecimal previousAverageTicket;
    private BigDecimal revenueChangePercentage;
    private BigDecimal closedTabsChangePercentage;
    private BigDecimal averageTicketChangePercentage;
}
