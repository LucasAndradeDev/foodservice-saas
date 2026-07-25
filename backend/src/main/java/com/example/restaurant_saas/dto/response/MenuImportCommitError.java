package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuImportCommitError {
    private String productName;
    private String reason;
}
