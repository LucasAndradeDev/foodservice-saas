package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.AdminUpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.service.AdminRestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin", description = "Platform admin: list restaurants, block/unblock, set payment due date")
public class AdminController {

    private final AdminRestaurantService adminRestaurantService;

    @GetMapping
    @Operation(summary = "List every restaurant", description = "Not scoped by tenant — only reachable with a platform-admin token.")
    public ResponseEntity<List<RestaurantResponse>> listRestaurants() {
        return ResponseEntity.ok(adminRestaurantService.listAll());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a restaurant's active status and payment due date")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(adminRestaurantService.updateStatus(id, request));
    }
}
