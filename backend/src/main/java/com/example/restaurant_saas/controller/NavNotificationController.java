package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.NavSection;
import com.example.restaurant_saas.dto.response.NavNotificationStatusResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.NavNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nav-notifications")
@RequiredArgsConstructor
@Tag(name = "Nav Notifications", description = "Unread-indicator status for the Kitchen, Tables and Checkout navigation tabs.")
public class NavNotificationController {

    private final NavNotificationService navNotificationService;

    @GetMapping("/status")
    @Operation(summary = "Get nav notification status", description = "Returns whether each navigation tab has unseen activity since it was last visited.")
    public ResponseEntity<NavNotificationStatusResponse> getStatus(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(navNotificationService.getStatus(currentUser.getRestaurantId()));
    }

    @PostMapping("/{section}/seen")
    @Operation(summary = "Mark a nav section as seen", description = "Clears the unseen indicator for the given section for the whole restaurant.")
    public ResponseEntity<Void> markSeen(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable NavSection section
    ) {
        navNotificationService.markSeen(currentUser.getRestaurantId(), section);
        return ResponseEntity.noContent().build();
    }
}
