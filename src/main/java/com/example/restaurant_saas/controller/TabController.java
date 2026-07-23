package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.TabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/tabs")
@RequiredArgsConstructor
@Tag(name = "Tabs", description = "Manage the restaurant's tabs (open/close a table's running bill). Write operations restricted to OWNER, MANAGER, WAITER and CASHIER.")
public class TabController {

    private final TabService tabService;

    @GetMapping
    @Operation(summary = "List tabs", description = "Lists the restaurant's tabs, optionally filtered by status.")
    public ResponseEntity<List<TabResponse>> listTabs(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by status") @RequestParam(required = false) TabStatus status
    ) {
        return ResponseEntity.ok(tabService.listTabs(currentUser.getRestaurantId(), status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tab", description = "Returns a single tab by id, including the tables linked to it, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab returned"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant")
    })
    public ResponseEntity<TabResponse> getTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tabService.getTab(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Open tab", description = "Opens a new tab linked to one or more tables. All tables must exist in the restaurant, be active and FREE; on success, all linked tables become OCCUPIED. The whole operation is atomic: if any table is unavailable, nothing changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tab opened"),
            @ApiResponse(responseCode = "400", description = "Validation error or one or more tables not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, or one or more tables are inactive or not FREE")
    })
    public ResponseEntity<TabResponse> openTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody OpenTabRequest request
    ) {
        TabResponse response = tabService.openTab(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Close tab", description = "Closes an open tab and frees all tables linked to it (OCCUPIED to FREE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab closed"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, or already closed"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<TabResponse> closeTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tabService.closeTab(currentUser.getRestaurantId(), id));
    }
}
