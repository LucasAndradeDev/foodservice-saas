package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.UserService;
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
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(userService.listUsers(currentUser.getRestaurantId(), role, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.getUser(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserRole actingRole = extractRole(currentUser);
        UserResponse response = userService.createUser(currentUser.getRestaurantId(), actingRole, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserRole actingRole = extractRole(currentUser);
        UserResponse response = userService.updateUser(currentUser.getRestaurantId(), currentUser.getId(), actingRole, id, request);
        return ResponseEntity.ok(response);
    }

    private UserRole extractRole(UserDetailsImpl currentUser) {
        String authority = currentUser.getAuthorities().iterator().next().getAuthority();
        return UserRole.valueOf(authority.replace("ROLE_", ""));
    }
}
