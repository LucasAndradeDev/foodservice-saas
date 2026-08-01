package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private String discountReason;
    private String discountAppliedBy;
    private OffsetDateTime discountAppliedAt;
    private String cancelledBy;
    private OffsetDateTime cancelledAt;
    private BigDecimal netSubtotal;
    private Boolean isComboHeader;
    private List<OrderItemResponse> children;
}
