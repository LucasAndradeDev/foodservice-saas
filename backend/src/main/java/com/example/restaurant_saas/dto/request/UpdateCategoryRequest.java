package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCategoryRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Size(max = 500, message = "Banner image URL must be at most 500 characters long")
    @Pattern(regexp = "^$|^https?://.+|^/.+", message = "Banner image URL must start with http:// or https://")
    private String bannerImageUrl;

    @Pattern(
            regexp = "^$|^(breakfast|combo|pizza|taco|hotdog|wrap|dumpling|seafood|fish|sandwich|fries|popcorn|soup|pasta|salad|chicken|meat|beer|wine|cocktail|drink|tea|coffee|bread|dessert|cake|cookie|donut)$",
            message = "Icon must be one of the supported icon keys"
    )
    private String icon;

    private Boolean active;
}
