package com.example.warehouse.controller;

import com.example.warehouse.dto.request.SsoExchangeRequest;
import com.example.warehouse.dto.response.WarehouseSsoResponse;
import com.example.warehouse.service.WarehouseSsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "SSO exchange - there is no password login in Armazém Morá, only handoff tokens minted by Morá.")
public class AuthController {

    private final WarehouseSsoService warehouseSsoService;

    @PostMapping("/sso")
    @Operation(summary = "Exchange a Morá handoff token for a session", description = "Verifies the ~60s single-use token minted by Morá's POST /api/v1/warehouse/handoff, upserts the RestaurantLink, and returns a session token for this app.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session token returned"),
            @ApiResponse(responseCode = "401", description = "Handoff token missing, expired, malformed or already used past expiry")
    })
    public ResponseEntity<WarehouseSsoResponse> sso(@Valid @RequestBody SsoExchangeRequest request) {
        return ResponseEntity.ok(warehouseSsoService.exchange(request.getToken()));
    }
}
