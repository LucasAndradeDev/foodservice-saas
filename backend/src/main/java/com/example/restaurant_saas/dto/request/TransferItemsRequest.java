package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TransferItemsRequest {

    @NotEmpty(message = "At least one item must be selected")
    private List<UUID> itemIds;

    @NotNull(message = "Target tab is required")
    private UUID targetTabId;
}
