package com.example.restaurant_saas.dto.response;

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
}
