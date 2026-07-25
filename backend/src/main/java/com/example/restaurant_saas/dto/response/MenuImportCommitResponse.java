package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MenuImportCommitResponse {
    private int categoriesCreated;
    private int categoriesReused;
    private int productsCreated;
    private List<MenuImportCommitError> skipped;
}
