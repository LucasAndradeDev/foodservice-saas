package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminUpdateRestaurantRequest {

    @NotNull(message = "Active is required")
    private Boolean active;

    // Full-replace, not partial-update-if-present: null explicitly clears the due date.
    private LocalDate paymentDueDate;
}
