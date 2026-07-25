package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ExtractedCategoryDto {
    private String tempId;
    private String name;
    private UUID matchedCategoryId;
}
