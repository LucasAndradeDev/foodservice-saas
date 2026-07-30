package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.AvailabilityWindowRequest;
import com.example.restaurant_saas.dto.response.AvailabilityWindowResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.ProductAvailabilityWindowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/availability-windows")
@RequiredArgsConstructor
@Tag(name = "Product Availability Windows", description = "Time windows in which a product can be ordered (e.g. lunch menu, alcohol only after a certain hour). Write operations restricted to OWNER and MANAGER.")
public class ProductAvailabilityWindowController {

    private final ProductAvailabilityWindowService windowService;

    @GetMapping
    @Operation(summary = "List availability windows", description = "Lists the availability windows configured for a product. No windows means the product is always available.")
    public ResponseEntity<List<AvailabilityWindowResponse>> listWindows(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(windowService.listWindows(currentUser.getRestaurantId(), productId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create availability window", description = "Creates a new availability window for a product.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Availability window created"),
            @ApiResponse(responseCode = "400", description = "Validation error or product not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<AvailabilityWindowResponse> createWindow(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @Valid @RequestBody AvailabilityWindowRequest request
    ) {
        AvailabilityWindowResponse response = windowService.createWindow(currentUser.getRestaurantId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{windowId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update availability window", description = "Replaces the day of week and time range of an existing availability window.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability window updated"),
            @ApiResponse(responseCode = "400", description = "Validation error, or product/window not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<AvailabilityWindowResponse> updateWindow(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @PathVariable UUID windowId,
            @Valid @RequestBody AvailabilityWindowRequest request
    ) {
        return ResponseEntity.ok(windowService.updateWindow(currentUser.getRestaurantId(), productId, windowId, request));
    }

    @DeleteMapping("/{windowId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete availability window", description = "Permanently deletes an availability window. If it was the product's last window, the product becomes always available.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Availability window deleted"),
            @ApiResponse(responseCode = "400", description = "Product or window not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<Void> deleteWindow(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @PathVariable UUID windowId
    ) {
        windowService.deleteWindow(currentUser.getRestaurantId(), productId, windowId);
        return ResponseEntity.noContent().build();
    }
}
