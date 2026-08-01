package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ComboSlotSelectionInput {

    @NotNull(message = "Slot id is required")
    private UUID slotId;

    @NotNull(message = "Selected product id is required")
    private UUID selectedProductId;
}
