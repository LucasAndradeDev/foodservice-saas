package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateTableRequest {

    @Min(value = 1, message = "Number must be at least 1")
    private Integer number;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private Boolean active;

    private UUID areaId;

    /**
     * Set to true to explicitly unassign the table's area.
     * areaId alone cannot express "clear" since a missing/null areaId means "leave unchanged".
     */
    private Boolean clearArea;
}
