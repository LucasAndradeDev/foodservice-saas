package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import com.example.restaurant_saas.domain.enums.DiscountType;
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

    // Null for a regular tab (table or Balcao). Set when this tab has a DeliveryDetails row
    // (task 27) - lets the frontend tell a delivery order apart from Balcao, both of which have
    // no tables, and show the delivery stage instead of a generic "open" badge.
    private DeliveryStatus deliveryStatus;
    private OffsetDateTime openedAt;
    private OffsetDateTime lastOrderAt;
    private OffsetDateTime closedAt;
    private BigDecimal billTotal;
    private BigDecimal amountPaid;
    private BigDecimal remainingBalance;
    private List<PaymentResponse> payments;
    private List<TabTableSummary> tables;
    private OffsetDateTime receiptPrintedAt;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String discountReason;
    private String discountAppliedBy;
    private OffsetDateTime discountAppliedAt;
    private BigDecimal serviceChargePercentage;
    private BigDecimal serviceChargeAmount;

    // Null for a regular tab (table or Balcao) - same presence check as deliveryStatus. Already
    // folded into billTotal (TabService#resolveBillTotal); exposed here too so staff screens can
    // show it as its own line instead of leaving a gap between the items subtotal and the total
    // with nothing explaining it, the same complaint the customer's own tracking page had.
    private BigDecimal deliveryFee;
}
