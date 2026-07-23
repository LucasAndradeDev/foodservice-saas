package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateOrderItemRequest> items;
}
