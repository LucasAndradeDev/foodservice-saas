package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private UUID restaurantId;
    private UUID tabId;
    private OffsetDateTime createdAt;
    private List<OrderItemResponse> items;
    private BigDecimal total;
    private OffsetDateTime printedAt;
}
