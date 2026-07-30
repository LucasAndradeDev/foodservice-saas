package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.ReorderDiningAreasRequest;
import com.example.restaurant_saas.dto.request.UpdateDiningAreaRequest;
import com.example.restaurant_saas.dto.response.DiningAreaResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.DiningAreaService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/dining-areas")
@RequiredArgsConstructor
@Tag(name = "Dining Areas", description = "Group tables by physical region of the dining room (e.g. Main hall, Balcony). Write operations restricted to OWNER and MANAGER.")
public class DiningAreaController {

    private final DiningAreaService diningAreaService;

    @GetMapping
    @Operation(summary = "List dining areas", description = "Lists the restaurant's dining areas ordered by display order.")
    public ResponseEntity<List<DiningAreaResponse>> listAreas(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(diningAreaService.listAreas(currentUser.getRestaurantId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create dining area", description = "Creates a new dining area at the end of the display order. Name must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dining area created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate name"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<DiningAreaResponse> createArea(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateDiningAreaRequest request
    ) {
        DiningAreaResponse response = diningAreaService.createArea(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Reorder dining areas", description = "Sets the display order of all the restaurant's dining areas at once. The request must include exactly the restaurant's current dining area ids.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dining areas reordered"),
            @ApiResponse(responseCode = "400", description = "orderedIds does not match the restaurant's dining areas"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<List<DiningAreaResponse>> reorderAreas(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody ReorderDiningAreasRequest request
    ) {
        return ResponseEntity.ok(diningAreaService.reorderAreas(currentUser.getRestaurantId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Rename dining area", description = "Renames a dining area. Name must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dining area updated"),
            @ApiResponse(responseCode = "400", description = "Dining area not found, validation error, or duplicate name"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<DiningAreaResponse> updateArea(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiningAreaRequest request
    ) {
        return ResponseEntity.ok(diningAreaService.updateArea(currentUser.getRestaurantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete dining area", description = "Permanently deletes a dining area. Only allowed while no table is assigned to it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dining area deleted"),
            @ApiResponse(responseCode = "400", description = "Dining area not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER, or area still has tables assigned")
    })
    public ResponseEntity<Void> deleteArea(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        diningAreaService.deleteArea(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }
}
