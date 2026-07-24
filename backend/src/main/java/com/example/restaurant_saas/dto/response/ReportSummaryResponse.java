package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ReportSummaryResponse {
    private BigDecimal totalRevenue;
    private long closedTabsCount;
    private BigDecimal averageTicket;
    private List<PaymentMethodTotalResponse> byPaymentMethod;
    private List<TopProductResponse> topProducts;
}
