package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.TableStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TableResponse {
    private UUID id;
    private UUID restaurantId;
    private Integer number;
    private TableStatus status;
    private Boolean active;
    private UUID areaId;
}
