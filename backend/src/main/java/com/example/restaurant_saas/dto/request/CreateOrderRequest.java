package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateOrderItemRequest> items;

    // Public self-order only: an optional WhatsApp number to notify when the order is ready.
    // Ignored by staff-side order creation, and only applied when this order is the one that
    // opens the tab -- see PublicOrderService/TabService#openOrGetTabForTable.
    @Size(max = 20, message = "Phone number is too long")
    private String customerPhone;
}
