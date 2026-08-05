package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ReservationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReservationResponse {
    private UUID id;
    private UUID restaurantId;
    private String customerName;
    private String customerPhone;
    private String note;
    private Integer partySize;
    private OffsetDateTime reservationTime;
    private ReservationStatus status;
    private String accessToken;
    private UUID tabId;
    private List<ReservationTableSummary> tables;
    private OffsetDateTime createdAt;
}
