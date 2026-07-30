package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DiningAreaResponse {
    private UUID id;
    private UUID restaurantId;
    private String name;
    private Integer displayOrder;
}
