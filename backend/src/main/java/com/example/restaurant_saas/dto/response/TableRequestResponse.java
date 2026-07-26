package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.TableRequestType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TableRequestResponse {
    private UUID id;
    private UUID tableId;
    private TableRequestType type;
    private OffsetDateTime requestedAt;
    private OffsetDateTime acknowledgedAt;
}
