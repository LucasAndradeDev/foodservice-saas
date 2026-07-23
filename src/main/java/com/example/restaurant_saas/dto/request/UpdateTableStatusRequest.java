package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.TableStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTableStatusRequest {

    @NotNull(message = "Status is required")
    private TableStatus status;
}
