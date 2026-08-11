package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
    // Only phone-shaped characters, with 10-15 digits once formatting is stripped (covers a
    // local number up to a full E.164 number with country code). Rejects garbage/non-phone input
    // up front; doesn't by itself stop someone entering a real number that isn't theirs -- see
    // the per-number rate limit in PublicOrderService for that.
    @Pattern(regexp = "^$|^(?=(?:\\D*\\d){10,15}\\D*$)[0-9+()\\-\\s]*$", message = "Invalid phone number")
    private String customerPhone;
}
