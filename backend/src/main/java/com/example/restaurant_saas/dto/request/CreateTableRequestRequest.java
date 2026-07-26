package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.TableRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTableRequestRequest {

    @NotNull(message = "Type is required")
    private TableRequestType type;
}
