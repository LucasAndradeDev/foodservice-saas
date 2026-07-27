package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String observation;
    private ItemStatus status;
    private List<OrderItemModifierResponse> modifiers;
    private BigDecimal subtotal;
}
