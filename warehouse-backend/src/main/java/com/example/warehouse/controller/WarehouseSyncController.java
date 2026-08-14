package com.example.warehouse.controller;

import com.example.warehouse.service.WarehouseSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Not user-facing: called by a GitHub Actions cron every 15 minutes, not by a logged-in user.
 * Authenticated by a shared-secret header instead of the normal session flow - same shape as
 * Morá's BackupController.
 */
@RestController
@RequestMapping("/api/v1/internal/sync")
@RequiredArgsConstructor
@Tag(name = "Sync (internal)", description = "Pulls delivered sales from Morá and deducts ingredient stock. Not part of the public API surface.")
public class WarehouseSyncController {

    private static final String TOKEN_HEADER = "X-Sync-Token";

    private final WarehouseSyncService warehouseSyncService;

    @Value("${sync.trigger-token}")
    private String expectedToken;

    @PostMapping
    @Operation(summary = "Run a stock sync for every restaurant", description = "Pulls delivered sales since each restaurant's last sync, explodes them via recipes, and deducts ingredient stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync completed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Sync-Token header")
    })
    public ResponseEntity<Void> triggerSync(@RequestHeader(value = TOKEN_HEADER, required = false) String providedToken) {
        if (!isValidToken(providedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        warehouseSyncService.syncAll();
        return ResponseEntity.ok().build();
    }

    private boolean isValidToken(String providedToken) {
        if (providedToken == null || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }
}
