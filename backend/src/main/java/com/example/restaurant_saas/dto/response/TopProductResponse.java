package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopProductResponse {
    private UUID productId;
    private String productName;
    private long quantitySold;
    private BigDecimal revenue;
    private long costQuantityCovered;
    private BigDecimal costTotal;
    private BigDecimal marginTotal;
    private BigDecimal marginPercentage;
}
