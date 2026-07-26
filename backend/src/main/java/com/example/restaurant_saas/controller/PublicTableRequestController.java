package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateTableRequestRequest;
import com.example.restaurant_saas.dto.response.TableRequestResponse;
import com.example.restaurant_saas.service.PublicTableRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/tables/{tableId}/requests")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicTableRequestController {

    private final PublicTableRequestService publicTableRequestService;

    @PostMapping
    @Operation(summary = "Create a table request", description = "Signals staff that a table needs attention (e.g. call waiter, request the bill), without authentication. If a request of the same type is already pending for this table, returns the existing one instead of creating a duplicate.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request created (or existing pending one returned)"),
            @ApiResponse(responseCode = "400", description = "Validation error, or restaurant/table not found")
    })
    public ResponseEntity<TableRequestResponse> createRequest(
            @PathVariable String slug,
            @PathVariable UUID tableId,
            @Valid @RequestBody CreateTableRequestRequest request
    ) {
        TableRequestResponse response = publicTableRequestService.createRequest(slug, tableId, request.getType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
