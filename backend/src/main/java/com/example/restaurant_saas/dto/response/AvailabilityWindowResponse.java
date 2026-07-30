package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class AvailabilityWindowResponse {
    private UUID id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
}
