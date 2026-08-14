package com.example.restaurant_saas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseSaleItemResponse {
    private UUID productId;
    private String productName;
    private Integer quantity;
    private OffsetDateTime deliveredAt;
}
