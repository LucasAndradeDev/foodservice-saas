package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Data
public class HappyHourRuleRequest {

    @NotNull(message = "Category is required")
    private UUID categoryId;

    private Set<DayOfWeek> daysOfWeek;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be greater than zero")
    private BigDecimal discountValue;

    @NotNull(message = "Active flag is required")
    private Boolean active;
}
