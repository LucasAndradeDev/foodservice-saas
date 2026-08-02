package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class CashWithdrawalResponse {
    private UUID id;
    private BigDecimal amount;
    private String reason;
    private String withdrawnByName;
    private OffsetDateTime withdrawnAt;
}
