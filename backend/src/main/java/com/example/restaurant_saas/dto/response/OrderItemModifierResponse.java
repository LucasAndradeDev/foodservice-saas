package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemModifierResponse {
    private String groupName;
    private String optionName;
    private BigDecimal priceDelta;
}
