package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class KitchenItemResponse {
    private UUID id;
    private UUID orderId;
    private List<Integer> tableNumbers;
    private String productName;
    private Integer quantity;
    private String observation;
    private List<OrderItemModifierResponse> modifiers;
    private ItemStatus status;
    private OffsetDateTime createdAt;
}
