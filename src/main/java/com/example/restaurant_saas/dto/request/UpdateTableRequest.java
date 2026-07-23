package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateTableRequest {

    @Min(value = 1, message = "Number must be at least 1")
    private Integer number;

    private Boolean active;
}
