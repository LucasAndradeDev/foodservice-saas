package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.response.WarehouseHandoffResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.security.WarehouseHandoffTokenService;
import com.example.restaurant_saas.service.WarehouseIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Warehouse (Armazém Morá)", description = "SSO handoff into the separate Armazém Morá app. Restricted to OWNER and MANAGER.")
public class WarehouseController {

    private final WarehouseIntegrationService warehouseIntegrationService;
    private final WarehouseHandoffTokenService handoffTokenService;
    private final RestaurantRepository restaurantRepository;

    @Value("${app.warehouse-frontend-url}")
    private String warehouseFrontendUrl;

    @PostMapping("/handoff")
    @Operation(summary = "Start SSO into Armazém Morá", description = "Mints a ~60s single-use handoff token (and a fresh long-lived integration API key, embedded in it) so the frontend can open Armazém Morá already authenticated, without a second login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Handoff URL returned")
    })
    public ResponseEntity<WarehouseHandoffResponse> handoff(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Restaurant restaurant = restaurantRepository.findById(userDetails.getRestaurantId())
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"));

        String rawApiKey = warehouseIntegrationService.rotateApiKey(restaurant.getId());
        String handoffToken = handoffTokenService.generateHandoffToken(restaurant.getId(), restaurant.getName(), rawApiKey);
        String handoffUrl = warehouseFrontendUrl + "/sso?token=" + handoffToken;

        return ResponseEntity.ok(WarehouseHandoffResponse.builder().handoffUrl(handoffUrl).build());
    }
}
