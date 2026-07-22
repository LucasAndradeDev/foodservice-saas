package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.UpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.RestaurantService;
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

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurants", description = "View and edit the logged-in user's restaurant settings")
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping("/me")
    @Operation(summary = "Get my restaurant", description = "Returns the restaurant data for the authenticated user's tenant. Available to any authenticated staff role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant data returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    public ResponseEntity<RestaurantResponse> getMyRestaurant(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(restaurantService.getMyRestaurant(currentUser.getRestaurantId()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update my restaurant settings", description = "Updates trade name, logo, primary color, table count, phone and address. Fields omitted from the request body are left unchanged. Restricted to OWNER and MANAGER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant updated"),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g. negative table count)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<RestaurantResponse> updateMyRestaurant(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(restaurantService.updateMyRestaurant(currentUser.getRestaurantId(), request));
    }
}
