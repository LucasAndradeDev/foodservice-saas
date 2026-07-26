package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ProductResponse {
    private UUID id;
    private UUID restaurantId;
    private UUID categoryId;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Boolean active;
    private Boolean soldOutToday;
}
