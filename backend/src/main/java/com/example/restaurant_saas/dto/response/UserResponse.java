package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private UUID restaurantId;
    private String name;
    private String email;
    private UserRole role;
    private Boolean active;
    private Boolean emailVerified;
    private OffsetDateTime termsAcceptedAt;
}
