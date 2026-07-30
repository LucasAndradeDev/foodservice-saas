package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemCouponRequest {

    @NotBlank(message = "Code is required")
    private String code;
}
