package com.example.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SupplierResponse {
    private UUID id;
    private String name;
    private String contact;
    private Boolean active;
}
