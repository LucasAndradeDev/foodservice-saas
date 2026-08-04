package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.BackupResponse;
import com.example.restaurant_saas.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Not user-facing: called by an external scheduler (GitHub Actions cron), not by a logged-in
 * user. Authenticated by a shared-secret header instead of the normal JWT flow — see
 * SecurityConfig, which permits this whole path through Spring Security's authorization so this
 * check can happen here.
 */
@RestController
@RequestMapping("/api/v1/internal/backups")
@RequiredArgsConstructor
@Tag(name = "Backups (internal)", description = "Triggers a full database backup. Not part of the public API surface.")
public class BackupController {

    private static final String TOKEN_HEADER = "X-Backup-Token";

    private final BackupService backupService;

    @Value("${backup.trigger-token}")
    private String expectedToken;

    @PostMapping
    @Operation(summary = "Run a database backup", description = "Dumps the database and uploads it to Supabase Storage, pruning old backups past the retention window.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backup completed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Backup-Token header"),
            @ApiResponse(responseCode = "500", description = "pg_dump or the upload to Supabase Storage failed")
    })
    public ResponseEntity<BackupResponse> run(HttpServletRequest request) {
        String providedToken = request.getHeader(TOKEN_HEADER);
        if (!isValidToken(providedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(backupService.runBackup());
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
