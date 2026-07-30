package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicMenuProductResponse {
    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Boolean soldOut;
    private Boolean availableNow;
    private Boolean featured;
    private Boolean bestseller;
    private Integer estimatedWaitMinutes;
    private List<ModifierGroupResponse> modifierGroups;
}
