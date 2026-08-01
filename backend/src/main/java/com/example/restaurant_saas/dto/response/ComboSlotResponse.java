package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ComboSlotResponse {
    private UUID id;
    private String name;
    private Boolean required;
    private List<ComboSlotOptionResponse> options;
}
