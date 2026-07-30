package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class CouponResponse {
    private UUID id;
    private UUID restaurantId;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Boolean active;
    private OffsetDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
}
