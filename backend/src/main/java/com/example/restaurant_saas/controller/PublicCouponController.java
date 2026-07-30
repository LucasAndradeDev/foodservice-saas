package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.RedeemCouponRequest;
import com.example.restaurant_saas.dto.response.PublicCouponRedemptionResponse;
import com.example.restaurant_saas.service.PublicCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/tables/{tableId}/coupon")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicCouponController {

    private final PublicCouponService publicCouponService;

    @PostMapping
    @Operation(summary = "Redeem a coupon", description = "Applies a coupon code to the table's tab (opening one automatically if none is open yet), reusing the tab's existing one-off discount fields. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon applied"),
            @ApiResponse(responseCode = "400", description = "Restaurant/table not found, or coupon code not found/inactive"),
            @ApiResponse(responseCode = "403", description = "Coupon has expired, reached its usage limit, or the table is not available for self-ordering")
    })
    public ResponseEntity<PublicCouponRedemptionResponse> redeem(
            @PathVariable String slug,
            @PathVariable UUID tableId,
            @Valid @RequestBody RedeemCouponRequest request
    ) {
        return ResponseEntity.ok(publicCouponService.redeem(slug, tableId, request));
    }
}
