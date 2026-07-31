package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PublicCouponRedemptionResponse {
    private String discountAppliedLabel;
    private DiscountType discountType;
    private BigDecimal discountValue;
}
