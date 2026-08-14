package com.example.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MoraProductResponse {
    private UUID productId;
    private String productName;
}
