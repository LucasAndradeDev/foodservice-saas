package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderItemStatusRequest {

    @NotNull(message = "Status is required")
    private ItemStatus status;
}
