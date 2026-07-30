package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicFeedbackContextResponse {
    private String restaurantName;
    private String logo;
    private List<Integer> tableNumbers;
    private boolean alreadySubmitted;
}
