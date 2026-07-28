package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicMenuReorderItemResponse {
    private UUID productId;
    private String productName;
    private Integer quantity;
    private String observation;
    private List<PublicMenuReorderModifierResponse> modifiers;
}
