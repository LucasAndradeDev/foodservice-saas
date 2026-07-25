package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ExtractedProductDto {
    private String tempId;
    private String categoryTempId;
    private String name;
    private String description;
    private BigDecimal price;
    private boolean duplicate;
    private UUID duplicateOfProductId;
}
