package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DeliveryZoneResponse {
    private UUID id;
    private UUID restaurantId;
    private String neighborhood;
    private BigDecimal fee;
    private Boolean active;
}
