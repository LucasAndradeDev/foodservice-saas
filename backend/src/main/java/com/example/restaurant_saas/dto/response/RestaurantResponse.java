package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RestaurantResponse {
    private UUID id;
    private String name;
    private String tradeName;
    private String slug;
    private String cnpj;
    private String phone;
    private String address;
    private String logo;
    private Integer tableCount;
    private Boolean active;
    private Boolean autoPrintKitchenTickets;
    private Integer kitchenWarningThresholdMinutes;
    private Integer kitchenCriticalThresholdMinutes;
}
