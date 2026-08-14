package com.example.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseSsoResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private String restaurantName;
}
