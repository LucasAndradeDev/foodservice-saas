package com.example.warehouse.controller;

import com.example.warehouse.dto.response.MoraProductResponse;
import com.example.warehouse.service.MoraProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mora-products")
@RequiredArgsConstructor
@Tag(name = "Morá Products", description = "Products fetched live from Morá, used to pick which product a recipe is for.")
public class MoraProductController {

    private final MoraProductService moraProductService;

    @GetMapping
    @Operation(summary = "List Morá products", description = "Lists this restaurant's active, non-combo products from Morá.")
    public ResponseEntity<List<MoraProductResponse>> listProducts(@AuthenticationPrincipal UUID restaurantLinkId) {
        return ResponseEntity.ok(moraProductService.listProducts(restaurantLinkId));
    }
}
