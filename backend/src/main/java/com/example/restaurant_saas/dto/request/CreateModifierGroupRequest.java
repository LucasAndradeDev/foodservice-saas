package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateModifierGroupRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @NotNull(message = "Selection type is required")
    private ModifierSelectionType selectionType;

    @NotNull(message = "Required flag is required")
    private Boolean required;

    @NotEmpty(message = "At least one option is required")
    @Valid
    private List<ModifierOptionInput> options;
}
