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
    // Zone-based or distance-based fee configured - gates whether the customer even sees a
    // "Delivery" mode option, instead of letting them fill an address that can never be quoted.
    private boolean deliveryAvailable;
}
