package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.UpdateComboCompositionRequest;
import com.example.restaurant_saas.dto.response.ComboCompositionResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.ComboService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/combo")
@RequiredArgsConstructor
@Tag(name = "Combos", description = "Composition (fixed items and choice slots) of a product of type COMBO. Write operations restricted to OWNER and MANAGER.")
public class ComboController {

    private final ComboService comboService;

    @GetMapping
    @Operation(summary = "Get combo composition", description = "Returns the fixed items, choice slots and calculated price range of a combo product.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Composition returned"),
            @ApiResponse(responseCode = "400", description = "Product not found in this restaurant")
    })
    public ResponseEntity<ComboCompositionResponse> getComposition(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(comboService.getComposition(currentUser.getRestaurantId(), productId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Replace combo composition", description = "Fully replaces the combo's fixed items and slots, and sets its discount percentage. The product must already be of type COMBO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Composition replaced"),
            @ApiResponse(responseCode = "400", description = "Product not found, not a COMBO, references another combo or itself, or validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<ComboCompositionResponse> updateComposition(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateComboCompositionRequest request
    ) {
        return ResponseEntity.ok(comboService.updateComposition(currentUser.getRestaurantId(), productId, request));
    }
}
