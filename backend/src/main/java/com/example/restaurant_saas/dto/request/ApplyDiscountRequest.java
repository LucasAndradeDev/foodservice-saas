package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.DiscountType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyDiscountRequest {

    /** Null clears any discount currently applied. */
    private DiscountType discountType;

    private BigDecimal discountValue;

    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;
}
