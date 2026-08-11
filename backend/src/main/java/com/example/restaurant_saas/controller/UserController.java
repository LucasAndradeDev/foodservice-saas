package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.UserService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Users", description = "Manage restaurant staff (OWNER/MANAGER/WAITER/KITCHEN/CASHIER). Restricted to OWNER and MANAGER.")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List staff", description = "Lists the restaurant's staff, optionally filtered by role and/or active status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff list returned"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<List<UserResponse>> listUsers(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by role") @RequestParam(required = false) UserRole role,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(userService.listUsers(currentUser.getRestaurantId(), role, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get staff member", description = "Returns a single staff member by id, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff member returned"),
            @ApiResponse(responseCode = "400", description = "User not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<UserResponse> getUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.getUser(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    @Operation(summary = "Create staff member", description = "Creates a new staff member with a random, never-shared password and emails them a set-password link (same mechanism as the forgot-password flow). OWNER can assign MANAGER, WAITER, KITCHEN or CASHIER; MANAGER can only assign WAITER, KITCHEN or CASHIER. The OWNER role can never be assigned through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Staff member created"),
            @ApiResponse(responseCode = "400", description = "Validation error or email already registered"),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to assign the requested role")
    })
    public ResponseEntity<UserResponse> createUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserRole actingRole = extractRole(currentUser);
        UserResponse response = userService.createUser(currentUser.getRestaurantId(), actingRole, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update staff member", description = "Updates name, role and/or active status. A user can never change their own role or active status, and the restaurant's last active OWNER cannot be deactivated. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff member updated"),
            @ApiResponse(responseCode = "400", description = "User not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Self role/active change, target out of caller's management scope, or would leave the restaurant without an active owner")
    })
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserRole actingRole = extractRole(currentUser);
        UserResponse response = userService.updateUser(currentUser.getRestaurantId(), currentUser.getId(), actingRole, id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/password-reset-link")
    @Operation(summary = "Send password reset link", description = "Emails a staff member a set-password link, same token/expiry mechanism as the forgot-password flow, triggered by an OWNER/MANAGER on their behalf. Cannot target the caller's own account (use the authenticated change-password flow) or an OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Link sent"),
            @ApiResponse(responseCode = "400", description = "User not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Target is the caller, an OWNER, or out of caller's management scope")
    })
    public ResponseEntity<Void> sendPasswordResetLink(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        UserRole actingRole = extractRole(currentUser);
        userService.sendPasswordResetLink(currentUser.getRestaurantId(), currentUser.getId(), actingRole, id);
        return ResponseEntity.noContent().build();
    }

    private UserRole extractRole(UserDetailsImpl currentUser) {
        String authority = currentUser.getAuthorities().iterator().next().getAuthority();
        return UserRole.valueOf(authority.replace("ROLE_", ""));
    }
}
