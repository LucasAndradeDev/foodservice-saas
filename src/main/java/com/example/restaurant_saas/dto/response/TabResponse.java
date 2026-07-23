package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.TabStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TabResponse {
    private UUID id;
    private UUID restaurantId;
    private TabStatus status;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private List<TabTableSummary> tables;
}
