package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixChargeResponse {
    private UUID id;
    private BigDecimal amount;
    private String brCode;
    private String qrCodeImage;
    private String paymentLinkUrl;
    // Only meaningful on the list endpoint (PENDING or PAID) - a freshly created charge is always
    // PENDING, so callers of createCharge don't need to check it there.
    private PixChargeStatus status;
}
