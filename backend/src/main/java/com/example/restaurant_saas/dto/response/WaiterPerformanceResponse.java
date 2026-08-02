package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WaiterPerformanceResponse {
    private List<WaiterPerformanceRowResponse> rows;
}
