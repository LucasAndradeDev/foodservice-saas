package com.example.warehouse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * All fields optional - a field left null in the request body is left unchanged, same
 * partial-update convention as UpdateIngredientRequest.
 */
@Data
public class UpdateSupplierRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Size(max = 200, message = "Contact must be at most 200 characters long")
    private String contact;

    private Boolean active;
}
