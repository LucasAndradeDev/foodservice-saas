package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

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
}
