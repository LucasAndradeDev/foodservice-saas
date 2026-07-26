package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.TableRequestResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.TableRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/table-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER')")
@Tag(name = "Table Requests", description = "Staff-facing view of pending table requests (call waiter, request bill) and acknowledgment. Restricted to OWNER, MANAGER and WAITER.")
public class TableRequestController {

    private final TableRequestService tableRequestService;

    @GetMapping
    @Operation(summary = "List pending table requests", description = "Lists table requests not yet acknowledged for the restaurant.")
    public ResponseEntity<List<TableRequestResponse>> listPending(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(tableRequestService.listPending(currentUser.getRestaurantId()));
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge a table request", description = "Marks a pending table request as handled by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request acknowledged"),
            @ApiResponse(responseCode = "400", description = "Request not found in this restaurant")
    })
    public ResponseEntity<TableRequestResponse> acknowledge(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tableRequestService.acknowledge(currentUser.getRestaurantId(), id, currentUser.getId()));
    }
}
