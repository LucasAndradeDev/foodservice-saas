package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Read-only line item for the customer's own delivery tracking page (DeliveryStatusPage) - no
 * status/observation/modifiers, since that level of kitchen detail isn't useful to the customer,
 * just what they ordered and how much it costs. */
@Data
@Builder
public class DeliveryItemResponse {
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
}
