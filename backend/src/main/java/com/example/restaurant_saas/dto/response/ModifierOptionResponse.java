package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ModifierOptionResponse {
    private UUID id;
    private String name;
    private BigDecimal priceDelta;
}
