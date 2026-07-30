package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.DiscountType;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class UpdateCouponRequest {

    private DiscountType discountType;

    @Positive(message = "Discount value must be greater than zero")
    private BigDecimal discountValue;

    private Boolean active;

    private OffsetDateTime expiresAt;
    private Boolean clearExpiresAt;

    @Positive(message = "Max uses must be greater than zero")
    private Integer maxUses;
    private Boolean clearMaxUses;
}
