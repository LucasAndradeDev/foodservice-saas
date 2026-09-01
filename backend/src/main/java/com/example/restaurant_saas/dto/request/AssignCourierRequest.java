package com.example.restaurant_saas.dto.request;

import lombok.Data;

import java.util.UUID;

// courierId is intentionally nullable - sending null unassigns the current courier (task 28.3).
@Data
public class AssignCourierRequest {
    private UUID courierId;
}
