package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ProductType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
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
    private List<String> galleryImageUrls;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Boolean active;
    private Boolean soldOutToday;
    private Boolean featured;
    private Boolean availableNow;
    private Boolean hasModifierGroups;
    private ProductType type;
    private BigDecimal discountPercentage;
}
