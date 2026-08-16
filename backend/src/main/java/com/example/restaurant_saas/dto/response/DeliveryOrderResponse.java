package com.example.restaurant_saas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DeliveryOrderResponse {

    private UUID tabId;

    // Unguessable link for the customer to check delivery status without an account (task 27) -
    // same idea as Reservation's access token. Not yet consumed by anything (the status page is
    // task 27.3), but returned now so the frontend has it from the moment the order exists.
    private String accessToken;

    private BigDecimal deliveryFee;

    private OrderResponse order;
}
