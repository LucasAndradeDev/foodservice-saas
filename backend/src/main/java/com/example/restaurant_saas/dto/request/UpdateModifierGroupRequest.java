package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateModifierGroupRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    private ModifierSelectionType selectionType;

    private Boolean required;

    @Valid
    @Size(min = 1, message = "At least one option is required")
    private List<ModifierOptionInput> options;
}
