package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.AddTableToTabRequest;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CancelPaymentRequest;
import com.example.restaurant_saas.dto.request.MergeTabRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.TabService;
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
@RequestMapping("/api/v1/tabs")
@RequiredArgsConstructor
@Tag(name = "Tabs", description = "Manage the restaurant's tabs (open/close a table's running bill). Write operations restricted to OWNER, MANAGER, WAITER and CASHIER.")
public class TabController {

    private final TabService tabService;

    @GetMapping
    @Operation(summary = "List tabs", description = "Lists the restaurant's tabs, optionally filtered by status.")
    public ResponseEntity<List<TabResponse>> listTabs(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by status") @RequestParam(required = false) TabStatus status
    ) {
        return ResponseEntity.ok(tabService.listTabs(currentUser.getRestaurantId(), status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tab", description = "Returns a single tab by id, including the tables linked to it, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab returned"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant")
    })
    public ResponseEntity<TabResponse> getTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tabService.getTab(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Open tab", description = "Opens a new tab linked to zero or more tables. An empty tableIds list opens a counter (Balcão) tab, not linked to any table. Otherwise, all tables must exist in the restaurant, be active, FREE, and not already linked to another open tab (can happen right after a payment cancellation, which leaves the table's status untouched); on success, all linked tables become OCCUPIED. The whole operation is atomic: if any table is unavailable, nothing changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tab opened"),
            @ApiResponse(responseCode = "400", description = "Validation error or one or more tables not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, or one or more tables are inactive, not FREE, or already linked to an open tab")
    })
    public ResponseEntity<TabResponse> openTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody OpenTabRequest request
    ) {
        TabResponse response = tabService.openTab(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/tables")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Add a free table to an open tab", description = "Attaches a table that is currently active and FREE to an already open tab, marking it OCCUPIED. Used when a group grows mid-service and grabs a neighboring empty table.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Table added to the tab"),
            @ApiResponse(responseCode = "400", description = "Tab or table not found in this restaurant, or tab is not open"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, or table is inactive or not FREE")
    })
    public ResponseEntity<TabResponse> addTable(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody AddTableToTabRequest request
    ) {
        return ResponseEntity.ok(tabService.addTable(currentUser.getRestaurantId(), id, request));
    }

    @PatchMapping("/{id}/merge")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Merge another open tab into this one", description = "Moves all tables and orders from the source tab into this one, then closes the source tab with status MERGED (not counted as a paid sale). Used when two separately-opened tabs decide to become a single bill.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tabs merged, returns the surviving (target) tab"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, source and target are the same tab, or either tab is not open"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<TabResponse> mergeTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody MergeTabRequest request
    ) {
        return ResponseEntity.ok(tabService.mergeTab(currentUser.getRestaurantId(), id, request));
    }

    @PatchMapping("/{id}/unmerge")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Undo a merge", description = "Reverses a previous merge: moves the source tab's tables and orders back out of this tab and reopens the source tab. Only works while this tab and the source tab haven't changed since the merge (target still open, source still MERGED). Meant for a short window right after merging, to fix a mistaken click.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merge undone, returns this tab in its restored state"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, this tab is not open, or the source tab was not merged into it"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<TabResponse> unmergeTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody MergeTabRequest request
    ) {
        return ResponseEntity.ok(tabService.unmergeTab(currentUser.getRestaurantId(), id, request.getSourceTabId()));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Pay and close tab", description = "Registers the payment and closes an open tab in the same step, freeing all tables linked to it (OCCUPIED to FREE). The paid amount must match the tab's total exactly, and all order items must already be DELIVERED or CANCELLED. serviceChargePercentage is only honored for OWNER/MANAGER; other roles always get the restaurant's configured default (or none, if disabled).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab paid and closed"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, already closed, or paid amount does not match the total"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, or tab has order items not yet DELIVERED or CANCELLED")
    })
    public ResponseEntity<TabResponse> payTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody PayTabRequest request
    ) {
        return ResponseEntity.ok(tabService.payTab(currentUser.getRestaurantId(), id, currentUser.getRole(), request));
    }

    @PatchMapping("/{id}/cancel-payment")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Correct a payment recorded by mistake", description = "Replaces a closed tab's payment record (method, amount, service charge) with a corrected one in a single step — e.g. the wrong method was picked, or the amount didn't match. The tab stays CLOSED throughout and its tables are never touched: unlike unmerge, this isn't meant to resume service, and by the time someone reaches this action the table may already be free or serving someone else entirely. serviceChargePercentage is honored as sent, since this endpoint is already OWNER/MANAGER-only. Restricted to OWNER and MANAGER; a reason is required for audit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment corrected"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, tab is not closed, reason missing, or corrected amount does not match the tab total"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<TabResponse> cancelPayment(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody CancelPaymentRequest request
    ) {
        return ResponseEntity.ok(tabService.cancelPayment(currentUser.getRestaurantId(), id, currentUser.getName(), request));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel tab", description = "Cancels an open tab that has no orders yet (e.g. opened by mistake), closing it without payment and freeing all linked tables. Not allowed once any order has been sent to the kitchen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab cancelled"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant or already closed"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, or tab already has orders")
    })
    public ResponseEntity<TabResponse> cancelTab(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tabService.cancelTab(currentUser.getRestaurantId(), id));
    }

    @PatchMapping("/{id}/discount")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Apply or clear a one-off discount on the whole tab", description = "Sets a fixed-amount or percentage discount applied on top of the tab's item total (after any per-item discounts), capped at that total. Send discountType as null to clear an existing discount. Restricted to OWNER and MANAGER; only allowed while the tab is OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount applied or cleared"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, tab is not open, or discount value is invalid"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<TabResponse> applyDiscount(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody ApplyDiscountRequest request
    ) {
        return ResponseEntity.ok(tabService.applyDiscount(currentUser.getRestaurantId(), id, currentUser.getName(), request));
    }

    @PatchMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Mark tab receipt as printed", description = "Records that this tab's bill/receipt was printed by setting receiptPrintedAt to now. Can be called again on reprint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tab marked as printed"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant")
    })
    public ResponseEntity<TabResponse> markReceiptPrinted(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tabService.markReceiptPrinted(currentUser.getRestaurantId(), id));
    }
}
