package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PeakHoursResponse {
    private List<PeakHourCellResponse> cells;
}
