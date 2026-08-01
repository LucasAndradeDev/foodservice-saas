package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class HappyHourRuleResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private List<DayOfWeek> daysOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Boolean active;
}
