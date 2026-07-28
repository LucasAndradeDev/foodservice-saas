package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PublicMenuOrderItemResponse {
    private UUID id;
    private String productName;
    private Integer quantity;
    private ItemStatus status;
}
