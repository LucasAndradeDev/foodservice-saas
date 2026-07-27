package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicMenuResponse {
    private String restaurantName;
    private String logo;
    private List<PublicMenuCategoryResponse> categories;
    private PublicMenuTableResponse table;
}
