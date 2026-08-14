package com.example.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecipeResponse {
    private UUID id;
    private UUID moraProductId;
    private String moraProductName;
    private List<RecipeItemResponse> items;
}
