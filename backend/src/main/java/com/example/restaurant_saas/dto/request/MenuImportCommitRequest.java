package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MenuImportCommitRequest {

    @NotEmpty(message = "At least one product is required")
    // Each entry runs its own DB round-trip in MenuImportService#commit (not batched, on purpose
    // - see the comment there), all inside one HTTP request/thread. A real menu import is a few
    // dozen rows; this cap is generous headroom above that, not a realistic ceiling, so it can't
    // be used to pin a request thread and hammer the DB with an oversized commit.
    @Size(max = 500, message = "At most 500 products are allowed per import")
    @Valid
    private List<MenuImportProductItem> products;
}
