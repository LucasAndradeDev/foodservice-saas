package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.DashboardResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated counters for the restaurant's current operation.")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get dashboard", description = "Returns free/occupied table counts, order items still not delivered (PENDING, PREPARING or READY) across all tabs, and today's revenue (sum of paid amounts for tabs closed today).")
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(dashboardService.getDashboard(currentUser.getRestaurantId()));
    }
}
