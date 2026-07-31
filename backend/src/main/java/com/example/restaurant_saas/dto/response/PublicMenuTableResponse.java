package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicMenuTableResponse {
    private UUID id;
    private Integer number;
    private boolean hasDeliveredItems;
    private List<PublicMenuOrderItemResponse> orderItems;
    private List<PublicMenuReorderItemResponse> lastOrderItems;
    private UUID currentTabId;
    private String discountAppliedLabel;
    private DiscountType discountType;
    private BigDecimal discountValue;
}
