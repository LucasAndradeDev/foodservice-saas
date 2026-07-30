package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FeedbackPageResponse {
    private List<FeedbackEntryResponse> entries;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
