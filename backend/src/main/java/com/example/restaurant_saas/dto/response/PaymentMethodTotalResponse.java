package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentMethodTotalResponse {
    private PaymentMethod paymentMethod;
    private BigDecimal total;
    private long paymentsCount;
}
