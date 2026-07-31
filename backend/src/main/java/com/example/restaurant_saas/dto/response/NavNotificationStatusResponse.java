package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NavNotificationStatusResponse {
    private boolean kitchen;
    private boolean tables;
    private boolean checkout;
}
