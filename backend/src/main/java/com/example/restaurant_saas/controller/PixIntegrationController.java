package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.SavePixIntegrationRequest;
import com.example.restaurant_saas.dto.response.PixIntegrationStatusResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.PixChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pix-integration")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Pix Integration", description = "Per-restaurant Woovi credential used to create Pix charges. Restricted to OWNER and MANAGER.")
public class PixIntegrationController {

    private final PixChargeService pixChargeService;

    @GetMapping
    @Operation(summary = "Check whether Pix payment is configured", description = "Never returns the stored AppID, encrypted or not - only whether one has been saved.")
    public ResponseEntity<PixIntegrationStatusResponse> getStatus(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(pixChargeService.getIntegrationStatus(currentUser.getRestaurantId()));
    }

    @PutMapping
    @Operation(summary = "Save (or replace) this restaurant's Woovi AppID", description = "The AppID is encrypted before being stored and is never returned by the API afterward.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "AppID saved")
    })
    public ResponseEntity<Void> saveIntegration(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody SavePixIntegrationRequest request
    ) {
        pixChargeService.saveIntegration(currentUser.getRestaurantId(), request.getAppId());
        return ResponseEntity.noContent().build();
    }
}
