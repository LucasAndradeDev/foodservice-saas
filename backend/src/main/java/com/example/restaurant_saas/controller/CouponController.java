package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCouponRequest;
import com.example.restaurant_saas.dto.request.UpdateCouponRequest;
import com.example.restaurant_saas.dto.response.CouponResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CouponService;
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
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Coupons", description = "Manage standalone discount coupons redeemed by customers in the self-service digital menu. Restricted to OWNER and MANAGER.")
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    @Operation(summary = "List coupons", description = "Lists the restaurant's coupons, most recently created first.")
    public ResponseEntity<List<CouponResponse>> listCoupons(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(couponService.listCoupons(currentUser.getRestaurantId()));
    }

    @PostMapping
    @Operation(summary = "Create coupon", description = "Creates a new coupon. Code must be unique within the restaurant (case-insensitive) and cannot be changed afterwards.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate code"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<CouponResponse> createCoupon(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateCouponRequest request
    ) {
        CouponResponse response = couponService.createCoupon(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update coupon", description = "Updates discount type/value, active status, expiration date and/or max uses. The code is fixed at creation — create a new coupon instead if you need a different one. Fields omitted from the request body are left unchanged; use clearExpiresAt/clearMaxUses to remove those limits instead of leaving them unlimited. Lowering max uses below the current usage count is allowed and simply makes the coupon stop working immediately.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon updated"),
            @ApiResponse(responseCode = "400", description = "Coupon not found or validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<CouponResponse> updateCoupon(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCouponRequest request
    ) {
        return ResponseEntity.ok(couponService.updateCoupon(currentUser.getRestaurantId(), id, request));
    }
}
