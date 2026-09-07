package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderItemRequest {

    @NotNull(message = "Product id is required")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    // Each unit of a COMBO product is exploded into several rows/queries (see
    // ComboExplodeService) inside a transaction that already holds a lock on the tab - an
    // unauthenticated public order endpoint accepting an unbounded quantity here would let a
    // single request hold that lock for an unreasonable amount of time.
    @Max(value = 100, message = "Quantity must be at most 100")
    private Integer quantity;

    @Size(max = 255, message = "Observation must be at most 255 characters long")
    private String observation;

    private List<UUID> selectedOptionIds;

    @Valid
    private List<ComboSlotSelectionInput> slotSelections;
}
