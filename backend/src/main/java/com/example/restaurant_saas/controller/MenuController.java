package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.PublicMenuResponse;
import com.example.restaurant_saas.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/{slug}")
    @Operation(summary = "Get public menu", description = "Returns the restaurant's branding plus its active categories and active products for the given slug. Optionally validates a tableId and echoes back its number, for self-ordering flows. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Menu returned"),
            @ApiResponse(responseCode = "400", description = "No restaurant found for this slug, or tableId does not match an active table in this restaurant")
    })
    public ResponseEntity<PublicMenuResponse> getPublicMenu(
            @PathVariable String slug,
            @RequestParam(required = false) UUID tableId
    ) {
        return ResponseEntity.ok(menuService.getPublicMenu(slug, tableId));
    }
}
