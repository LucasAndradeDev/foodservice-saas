package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class CreateCouponRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must be at most 30 characters long")
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be greater than zero")
    private BigDecimal discountValue;

    private OffsetDateTime expiresAt;

    @Positive(message = "Max uses must be greater than zero")
    private Integer maxUses;
}
