package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BackupResponse {
    private String filename;
    private long sizeBytes;
    private long durationMs;
    private int deletedCount;
}
