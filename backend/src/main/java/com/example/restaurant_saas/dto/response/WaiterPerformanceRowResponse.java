package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WaiterPerformanceRowResponse {
    private UUID waiterId;
    private String waiterName;
    private Boolean active;
    private BigDecimal totalSales;
    private long orderCount;
    private Integer averageServiceTimeMinutes;
}
