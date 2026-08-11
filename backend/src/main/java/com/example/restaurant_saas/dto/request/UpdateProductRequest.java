package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateProductRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters long")
    private String description;

    @Size(max = 500, message = "Image URL must be at most 500 characters long")
    // Either an uploaded file's own URL or one the user pasted in directly (the product form
    // supports both) - restrict to http(s) so a javascript:/data:/file: URI can't end up stored
    // and later rendered somewhere less inert than an <img src="...">.
    @Pattern(regexp = "^$|^https?://.+|^/.+", message = "Image URL must start with http:// or https://")
    private String imageUrl;

    private List<@Size(max = 500) @Pattern(regexp = "^$|^https?://.+|^/.+", message = "Image URL must start with http:// or https://") String> galleryImageUrls;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Cost price must not be negative")
    private BigDecimal costPrice;

    private UUID categoryId;

    private Boolean active;

    private Boolean soldOut;

    private Boolean featured;
}
