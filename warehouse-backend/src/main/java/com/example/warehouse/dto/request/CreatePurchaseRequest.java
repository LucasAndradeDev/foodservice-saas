package com.example.warehouse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePurchaseRequest {

    @NotNull(message = "Supplier is required")
    private UUID supplierId;

    // Defaults to now when omitted - see PurchaseService.
    private OffsetDateTime purchasedAt;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreatePurchaseItemRequest> items;
}
