package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.UpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping("/me")
    public ResponseEntity<RestaurantResponse> getMyRestaurant(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(restaurantService.getMyRestaurant(currentUser.getRestaurantId()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ResponseEntity<RestaurantResponse> updateMyRestaurant(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(restaurantService.updateMyRestaurant(currentUser.getRestaurantId(), request));
    }
}
