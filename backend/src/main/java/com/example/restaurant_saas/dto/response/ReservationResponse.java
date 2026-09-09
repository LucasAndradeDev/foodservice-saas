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
    // Lets the public status page (no login, reached only via the access token link) show which
    // restaurant this is and a way to contact them - same fields DeliveryDetailsResponse already
    // exposes for the equivalent delivery-tracking page. Harmless on the staff-facing responses
    // that share this same DTO (create/list/checkIn/cancel) - staff already know their own
    // restaurant's public info.
    private String restaurantSlug;
    private String restaurantName;
    private String restaurantPhone;
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
