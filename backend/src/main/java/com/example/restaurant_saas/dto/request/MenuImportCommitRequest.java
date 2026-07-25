package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MenuImportCommitRequest {

    @NotEmpty(message = "At least one product is required")
    @Valid
    private List<MenuImportProductItem> products;
}
