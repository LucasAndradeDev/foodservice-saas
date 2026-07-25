package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MenuImportPreviewResponse {
    private List<ExtractedCategoryDto> categories;
    private List<ExtractedProductDto> products;
    private List<String> warnings;
}
