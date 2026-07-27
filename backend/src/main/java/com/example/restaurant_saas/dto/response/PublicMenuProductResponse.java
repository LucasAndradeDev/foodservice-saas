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
    private List<ModifierGroupResponse> modifierGroups;
}
