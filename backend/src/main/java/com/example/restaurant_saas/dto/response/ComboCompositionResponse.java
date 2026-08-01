package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ComboCompositionResponse {
    private UUID productId;
    private BigDecimal discountPercentage;
    private List<ComboItemResponse> fixedItems;
    private List<ComboSlotResponse> slots;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
