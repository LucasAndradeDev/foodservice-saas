package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private PaymentStatus status;
    private OffsetDateTime paidAt;
    private String createdByName;
    private String voidedByName;
    private OffsetDateTime voidedAt;
    private String voidReason;
}
