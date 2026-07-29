package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;

@Data
@Builder
public class PeakHourCellResponse {
    private DayOfWeek dayOfWeek;
    private int hour;
    private BigDecimal avgOccupiedTables;
    private BigDecimal avgOrderCount;
    private int sampleCount;
}
