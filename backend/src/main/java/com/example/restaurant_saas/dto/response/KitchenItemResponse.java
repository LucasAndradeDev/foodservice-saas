package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private UUID tabId;
    private List<Integer> tableNumbers;
    // Tables empty + this true = delivery order (task 27); tables empty + this false = Balcao.
    // Both have no tables, so the kitchen queue needs this to tell them apart in its grouping label.
    // Explicit @JsonProperty because Jackson strips the "is" prefix off primitive-boolean getters
    // by default (isDelivery() -> "delivery"), which silently broke the frontend's isDelivery check.
    @JsonProperty("isDelivery")
    private boolean isDelivery;
    // Only set when isDelivery is true. The kitchen groups delivery cards by address, not by tab -
    // two separate orders to the same address (a repeat customer, or literally the same delivery
    // run) belong on one card; customerName only comes into play to tell apart two different
    // customers who happen to share an address string (a condo building), see KitchenPage#groupByTable.
    private String deliveryCustomerName;
    private String deliveryAddress;
    private String productName;
    private Integer quantity;
    private String observation;
    private List<OrderItemModifierResponse> modifiers;
    private ItemStatus status;
    private OffsetDateTime createdAt;
    private Boolean isComboHeader;
    private List<KitchenItemResponse> children;
}
