package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTablesBulkRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.dto.request.UpdateTableStatusRequest;
import com.example.restaurant_saas.dto.response.TableResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Manage the restaurant's tables. Write operations restricted to OWNER and MANAGER; status changes also allowed for WAITER.")
public class TableController {

    private final TableService tableService;

    @GetMapping
    @Operation(summary = "List tables", description = "Lists the restaurant's tables, optionally filtered by status and active flag.")
    public ResponseEntity<List<TableResponse>> listTables(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by status") @RequestParam(required = false) TableStatus status,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(tableService.listTables(currentUser.getRestaurantId(), status, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get table", description = "Returns a single table by id, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Table returned"),
            @ApiResponse(responseCode = "400", description = "Table not found in this restaurant")
    })
    public ResponseEntity<TableResponse> getTable(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tableService.getTable(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create table", description = "Creates a new table with status FREE. Number must be unique within the restaurant; if omitted, it is assigned automatically as the next available number.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Table created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate number"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<TableResponse> createTable(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateTableRequest request
    ) {
        TableResponse response = tableService.createTable(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create tables in bulk", description = "Creates the given quantity of tables at once, numbered sequentially starting right after the highest existing table number for the restaurant. All created tables start with status FREE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tables created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<List<TableResponse>> createTablesBulk(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateTablesBulkRequest request
    ) {
        List<TableResponse> response = tableService.createTablesBulk(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update table", description = "Updates number and/or active status. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Table updated"),
            @ApiResponse(responseCode = "400", description = "Table not found, validation error, or duplicate number"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<TableResponse> updateTable(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTableRequest request
    ) {
        return ResponseEntity.ok(tableService.updateTable(currentUser.getRestaurantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete table", description = "Permanently deletes a table. Only allowed while the table is FREE, to avoid removing a table that is part of an in-progress tab.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Table deleted"),
            @ApiResponse(responseCode = "400", description = "Table not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER, or table is not FREE")
    })
    public ResponseEntity<Void> deleteTable(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        tableService.deleteTable(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER')")
    @Operation(summary = "Update table status", description = "Manually changes the table status (FREE, OCCUPIED, CLOSING). Any transition is allowed for now; Sprint 5 will drive this transition automatically from the Tab flow.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Table not found or validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER, MANAGER or WAITER")
    })
    public ResponseEntity<TableResponse> updateTableStatus(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTableStatusRequest request
    ) {
        return ResponseEntity.ok(tableService.updateTableStatus(currentUser.getRestaurantId(), id, request));
    }
}
