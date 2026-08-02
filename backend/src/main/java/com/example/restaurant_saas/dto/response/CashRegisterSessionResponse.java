package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.CashRegisterSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CashRegisterSessionResponse {
    private UUID id;
    private CashRegisterSessionStatus status;
    private OffsetDateTime openedAt;
    private String openedByName;
    private BigDecimal openingAmount;
    private BigDecimal expectedAmount;
    private BigDecimal countedAmount;
    private BigDecimal differenceAmount;
    private OffsetDateTime closedAt;
    private String closedByName;
    private String closingNotes;
    private List<CashWithdrawalResponse> withdrawals;
}
