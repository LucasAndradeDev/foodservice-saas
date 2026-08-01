package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateComboCompositionRequest {

    @NotNull(message = "Discount percentage is required")
    @DecimalMin(value = "0.00", message = "Discount percentage must not be negative")
    @DecimalMax(value = "100.00", message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage;

    @NotNull(message = "Fixed items list is required (can be empty)")
    @Valid
    private List<ComboItemInput> fixedItems;

    @NotNull(message = "Slots list is required (can be empty)")
    @Valid
    private List<ComboSlotInput> slots;
}
