package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.TabStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TabResponse {
    private UUID id;
    private UUID restaurantId;
    private TabStatus status;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private PaymentMethod paymentMethod;
    private BigDecimal paidAmount;
    private OffsetDateTime paidAt;
    private List<TabTableSummary> tables;
    private OffsetDateTime receiptPrintedAt;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String discountReason;
    private String discountAppliedBy;
    private OffsetDateTime discountAppliedAt;
    private BigDecimal serviceChargePercentage;
    private BigDecimal serviceChargeAmount;
    private String paymentCancelledBy;
    private OffsetDateTime paymentCancelledAt;
    private String paymentCancelReason;
}
