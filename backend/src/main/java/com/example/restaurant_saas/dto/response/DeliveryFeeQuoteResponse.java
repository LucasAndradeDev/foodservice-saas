package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DeliveryFeeMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DeliveryFeeQuoteResponse {
    private boolean available;
    private BigDecimal fee;
    // Null when available is false, or when method is ZONE (no distance to show).
    private BigDecimal distanceKm;
    // Null when available is false. Lets the cart tell the customer "3,4 km" vs "taxa pro bairro X".
    private DeliveryFeeMethod method;
}
