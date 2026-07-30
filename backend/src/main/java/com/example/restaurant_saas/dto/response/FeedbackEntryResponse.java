package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class FeedbackEntryResponse {
    private Integer rating;
    private String comment;
    private OffsetDateTime createdAt;
    private List<Integer> tableNumbers;
}
