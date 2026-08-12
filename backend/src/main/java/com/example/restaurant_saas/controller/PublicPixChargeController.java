package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.PixChargeResponse;
import com.example.restaurant_saas.service.PixChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/tables/{tableId}/pix-charges")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicPixChargeController {

    private final PixChargeService pixChargeService;

    @PostMapping
    @Operation(summary = "Create a Pix charge for this table, started by the customer", description = "Same freeze-and-generate-QR-code flow as the staff Caixa endpoint, but reachable without authentication for a customer paying on their own from the digital menu. Requires the table to have an open tab with at least one DELIVERED item (same gate as requesting the bill) and the restaurant to have configured its Woovi AppID. Confirmed asynchronously by a webhook - the table's currentTabId disappearing from GET /public/menu/{slug} is how the digital menu already detects the tab closed.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Charge created, returns the Pix QR code/copy-paste code"),
            @ApiResponse(responseCode = "400", description = "Restaurant/table not found, table has no open tab, or no delivered items yet"),
            @ApiResponse(responseCode = "403", description = "This restaurant hasn't configured Pix payment yet"),
            @ApiResponse(responseCode = "502", description = "Woovi could not be reached or returned an unexpected response")
    })
    public ResponseEntity<PixChargeResponse> createCharge(
            @PathVariable String slug,
            @PathVariable UUID tableId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pixChargeService.createChargeForTable(slug, tableId));
    }
}
