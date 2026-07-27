package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ModifierGroupResponse {
    private UUID id;
    private String name;
    private ModifierSelectionType selectionType;
    private Boolean required;
    private List<ModifierOptionResponse> options;
}
