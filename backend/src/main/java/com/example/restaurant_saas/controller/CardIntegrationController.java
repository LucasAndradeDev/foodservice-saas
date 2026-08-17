package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.SaveCardIntegrationRequest;
import com.example.restaurant_saas.dto.response.CardIntegrationStatusResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CardChargeService;
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
@RequestMapping("/api/v1/card-integration")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Card Integration", description = "Per-restaurant Mercado Pago credentials used to create card charges. Restricted to OWNER and MANAGER.")
public class CardIntegrationController {

    private final CardChargeService cardChargeService;

    @GetMapping
    @Operation(summary = "Check whether card payment is configured", description = "Never returns the stored access token/webhook secret, encrypted or not - only whether both have been saved.")
    public ResponseEntity<CardIntegrationStatusResponse> getStatus(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(cardChargeService.getIntegrationStatus(currentUser.getRestaurantId()));
    }

    @PutMapping
    @Operation(summary = "Save (or replace) this restaurant's Mercado Pago credentials", description = "The access token and webhook secret are encrypted before being stored and are never returned by the API afterward. Each field is independently optional when rotating an already-connected integration - a blank one keeps the existing value; both are required together only the first time.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credentials saved"),
            @ApiResponse(responseCode = "400", description = "Missing both fields on first connection, or both left blank on an update")
    })
    public ResponseEntity<Void> saveIntegration(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody SaveCardIntegrationRequest request
    ) {
        cardChargeService.saveIntegration(currentUser.getRestaurantId(), request.getAccessToken(), request.getWebhookSecret());
        return ResponseEntity.noContent().build();
    }
}
