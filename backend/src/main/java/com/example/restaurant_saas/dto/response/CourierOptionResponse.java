package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

// Deliberately narrower than UserResponse: this backs the assignable-courier dropdown on the
// Delivery operation screen, reachable by WAITER/KITCHEN/CASHIER too, none of whom should get a
// courier's email or other account details back just to assign them to an order.
@Data
@Builder
public class CourierOptionResponse {
    private UUID id;
    private String name;
    private Boolean active;
}
