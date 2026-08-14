package com.example.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseResponse {
    private UUID id;
    private UUID supplierId;
    private String supplierName;
    private OffsetDateTime purchasedAt;
    private OffsetDateTime createdAt;
    private List<PurchaseItemResponse> items;
}
