package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateModifierGroupRequest;
import com.example.restaurant_saas.dto.request.UpdateModifierGroupRequest;
import com.example.restaurant_saas.dto.response.ModifierGroupResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.ProductModifierGroupService;
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
@RequestMapping("/api/v1/products/{productId}/modifier-groups")
@RequiredArgsConstructor
@Tag(name = "Product Modifiers", description = "Standardized modifier groups/options for a product (e.g. size, doneness). Write operations restricted to OWNER and MANAGER.")
public class ProductModifierGroupController {

    private final ProductModifierGroupService modifierGroupService;

    @GetMapping
    @Operation(summary = "List modifier groups", description = "Lists the modifier groups (with their options) configured for a product.")
    public ResponseEntity<List<ModifierGroupResponse>> listGroups(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(modifierGroupService.listGroups(currentUser.getRestaurantId(), productId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create modifier group", description = "Creates a modifier group and its full list of options in one request.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Modifier group created"),
            @ApiResponse(responseCode = "400", description = "Validation error or product not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<ModifierGroupResponse> createGroup(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateModifierGroupRequest request
    ) {
        ModifierGroupResponse response = modifierGroupService.createGroup(currentUser.getRestaurantId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update modifier group", description = "Updates group-level fields and/or fully replaces its option list. Omitted fields are left unchanged; when options are provided, the entire existing option list is replaced.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Modifier group updated"),
            @ApiResponse(responseCode = "400", description = "Validation error, or product/group not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<ModifierGroupResponse> updateGroup(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateModifierGroupRequest request
    ) {
        return ResponseEntity.ok(modifierGroupService.updateGroup(currentUser.getRestaurantId(), productId, groupId, request));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete modifier group", description = "Permanently deletes a modifier group and its options. Past orders keep an independent snapshot of any modifiers chosen, so this is always safe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Modifier group deleted"),
            @ApiResponse(responseCode = "400", description = "Product or group not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID productId,
            @PathVariable UUID groupId
    ) {
        modifierGroupService.deleteGroup(currentUser.getRestaurantId(), productId, groupId);
        return ResponseEntity.noContent().build();
    }
}
