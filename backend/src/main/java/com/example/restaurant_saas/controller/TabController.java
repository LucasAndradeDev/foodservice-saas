package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AddTableToTabRequest;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CreateCardChargeRequest;
import com.example.restaurant_saas.dto.request.CreatePixChargeRequest;
import com.example.restaurant_saas.dto.request.MergeTabRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.VoidPaymentRequest;
import com.example.restaurant_saas.dto.response.CardChargeResponse;
import com.example.restaurant_saas.dto.response.PixChargeResponse;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CardChargeService;
import com.example.restaurant_saas.service.PixChargeService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tabs")
@RequiredArgsConstructor
@Tag(name = "Tabs", description = "Manage the restaurant's tabs (open/close a table's running bill). Write operations restricted to OWNER, MANAGER, WAITER and CASHIER.")
public class TabController {

    private final TabService tabService;
    private final PixChargeService pixChargeService;
    private final CardChargeService cardChargeService;

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

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Register one or more payments (split bill)", description = "Registers one or more payments in a single atomic call — e.g. a bill split across several people and/or payment methods. Works on an OPEN tab (closing it and freeing its tables once the sum of all active payments reaches the total), or, OWNER/MANAGER only, on a CLOSED tab left with a debt by a previous payment void. serviceChargePercentage is only honored on the tab's first payment (when its bill total is not yet locked), and only for OWNER/MANAGER when explicitly sent - omitting it (or any other role sending it) always falls back to the restaurant's configured default rather than waiving it. The sum of the payments sent must not exceed the tab's remaining balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment(s) registered; tab closed if fully paid"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, tab is not open (and has no debt to complete), or the payments sent exceed the remaining balance"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission, tab has order items not yet DELIVERED or CANCELLED, or a non-OWNER/MANAGER tried to complete a closed tab's debt")
    })
    public ResponseEntity<TabResponse> registerPayments(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody RegisterPaymentsRequest request
    ) {
        return ResponseEntity.ok(tabService.registerPayments(currentUser.getRestaurantId(), id, currentUser.getRole(), currentUser.getId(), request));
    }

    @PostMapping("/{id}/pix-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Create a Pix charge for this tab", description = "Freezes the tab's bill total (same freeze as the first registered payment) and asks Woovi for a QR code. An OWNER/MANAGER caller may pass serviceChargePercentage to override the restaurant's default, same as registerPayments - any other role's override is ignored. Omitting amount charges the tab's full remaining balance (the common case); a caller splitting the bill passes an explicit partial amount instead, one call per QR code - rejected if it exceeds what's not already paid or claimed by another still-PENDING charge on the same tab. The charge is confirmed asynchronously by a webhook - poll GET /tabs/{id} and watch for the tab closing/its payments list updating, or GET this same path to list every outstanding charge, to know when it's been paid. Requires the restaurant to have configured its Woovi AppID first (see /pix-integration).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge created, returns the Pix QR code/copy-paste code"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, tab is not open, or the requested amount exceeds the tab's remaining uncommitted balance"),
            @ApiResponse(responseCode = "403", description = "This restaurant hasn't configured Pix payment yet, or the tab has order items not yet DELIVERED or CANCELLED"),
            @ApiResponse(responseCode = "502", description = "Woovi could not be reached or returned an unexpected response")
    })
    public ResponseEntity<PixChargeResponse> createPixCharge(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @RequestBody(required = false) CreatePixChargeRequest request
    ) {
        // serviceChargePercentage itself has to actually be present to count as an override -
        // "body missing" and "body present only for amount, with no opinion on service charge"
        // both need to fall back to the restaurant's default; if this
        // instead keyed off request-object presence alone, an OWNER/MANAGER splitting the bill
        // (which needs to send amount in this same body) would unintentionally waive the service
        // charge to zero on every split entry just by not also specifying a percentage. Waiving it
        // on purpose is still possible - send serviceChargePercentage: 0, not null.
        BigDecimal requestedServiceChargePercentage = request != null ? request.getServiceChargePercentage() : null;
        boolean allowServiceChargeOverride = requestedServiceChargePercentage != null
                && (currentUser.getRole() == UserRole.OWNER || currentUser.getRole() == UserRole.MANAGER);
        BigDecimal requestedAmount = request != null ? request.getAmount() : null;
        return ResponseEntity.ok(pixChargeService.createCharge(
                currentUser.getRestaurantId(), id, allowServiceChargeOverride, requestedServiceChargePercentage, requestedAmount));
    }

    @GetMapping("/{id}/pix-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "List this tab's outstanding Pix charges", description = "Every PENDING or PAID charge on the tab (CANCELLED/EXPIRED ones are omitted) - lets the Checkout poll and reconcile several concurrent split-comanda QR codes at once by id, telling a charge that just got paid apart from one that was cancelled/expired elsewhere.")
    @ApiResponse(responseCode = "200", description = "List of outstanding charges, possibly empty")
    public ResponseEntity<List<PixChargeResponse>> listPixCharges(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(pixChargeService.listCharges(currentUser.getRestaurantId(), id));
    }

    @DeleteMapping("/{id}/pix-charges/{chargeId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel one specific pending Pix charge", description = "Cancels a single still-PENDING charge by id (one entry in a split-comanda payment) and unfreezes the tab's bill total only if nothing else - a real payment, or a sibling still-PENDING charge - is still relying on it. A no-op if the charge isn't PENDING anymore (already paid, or already cancelled/expired).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Charge cancelled (or was already resolved) - bill total unfrozen if nothing else needs it"),
            @ApiResponse(responseCode = "400", description = "Pix charge not found on this tab/restaurant")
    })
    public ResponseEntity<Void> cancelPixCharge(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @PathVariable UUID chargeId
    ) {
        pixChargeService.cancelCharge(currentUser.getRestaurantId(), id, chargeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pix-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel every pending Pix charge for this tab", description = "Cancels all still-PENDING Pix charges on this tab and, if nothing else is still relying on it (no active payment landed yet, no sibling PENDING Pix/card charge), unfreezes its bill total so items/discount/service-charge become editable again. A no-op if there's nothing PENDING (e.g. it was already paid, or there never was one).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pending charge(s), if any, cancelled; bill total unfrozen only if nothing else still needs it"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, or tab is not open"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<Void> cancelAllPixCharges(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        pixChargeService.cancelPendingCharge(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/card-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Create a card charge for this tab", description = "Freezes the tab's bill total (same freeze as the first registered payment) and asks Mercado Pago for a Checkout Pro preference. An OWNER/MANAGER caller may pass serviceChargePercentage to override the restaurant's default, same as registerPayments - any other role's override is ignored. Omitting amount charges the tab's full remaining balance (the common case); a caller splitting the bill passes an explicit partial amount instead, one call per checkout link - rejected if it exceeds what's not already paid or claimed by another still-PENDING Pix or card charge on the same tab. The charge is confirmed asynchronously by a webhook - poll GET /tabs/{id} and watch for the tab closing/its payments list updating, or GET this same path to list every outstanding charge, to know when it's been paid. Requires the restaurant to have configured Mercado Pago first (see /card-integration).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge created, returns the Mercado Pago checkout link"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, tab is not open, or the requested amount exceeds the tab's remaining uncommitted balance"),
            @ApiResponse(responseCode = "403", description = "This restaurant hasn't configured card payment yet, or the tab has order items not yet DELIVERED or CANCELLED"),
            @ApiResponse(responseCode = "502", description = "Mercado Pago could not be reached or returned an unexpected response")
    })
    public ResponseEntity<CardChargeResponse> createCardCharge(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @RequestBody(required = false) CreateCardChargeRequest request
    ) {
        BigDecimal requestedServiceChargePercentage = request != null ? request.getServiceChargePercentage() : null;
        boolean allowServiceChargeOverride = requestedServiceChargePercentage != null
                && (currentUser.getRole() == UserRole.OWNER || currentUser.getRole() == UserRole.MANAGER);
        BigDecimal requestedAmount = request != null ? request.getAmount() : null;
        return ResponseEntity.ok(cardChargeService.createCharge(
                currentUser.getRestaurantId(), id, allowServiceChargeOverride, requestedServiceChargePercentage, requestedAmount));
    }

    @GetMapping("/{id}/card-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "List this tab's outstanding card charges", description = "Every PENDING, PAID or DECLINED charge on the tab (CANCELLED/EXPIRED ones are omitted) - lets the Checkout poll and reconcile concurrent split-comanda checkout links at once by id, and show a specific decline message when one fails.")
    @ApiResponse(responseCode = "200", description = "List of outstanding charges, possibly empty")
    public ResponseEntity<List<CardChargeResponse>> listCardCharges(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(cardChargeService.listCharges(currentUser.getRestaurantId(), id));
    }

    @DeleteMapping("/{id}/card-charges/{chargeId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel one specific pending card charge", description = "Cancels a single still-PENDING charge by id (one entry in a split-comanda payment) and unfreezes the tab's bill total only if nothing else - a real payment, or a sibling still-PENDING charge - is still relying on it. A no-op if the charge isn't PENDING anymore.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Charge cancelled (or was already resolved) - bill total unfrozen if nothing else needs it"),
            @ApiResponse(responseCode = "400", description = "Card charge not found on this tab/restaurant")
    })
    public ResponseEntity<Void> cancelCardCharge(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @PathVariable UUID chargeId
    ) {
        cardChargeService.cancelCharge(currentUser.getRestaurantId(), id, chargeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/card-charges")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel every pending card charge for this tab", description = "Cancels all still-PENDING card charges on this tab and, if nothing else is still relying on it (no active payment landed yet, no sibling PENDING Pix/card charge), unfreezes its bill total so items/discount/service-charge become editable again. A no-op if there's nothing PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pending charge(s), if any, cancelled; bill total unfrozen only if nothing else still needs it"),
            @ApiResponse(responseCode = "400", description = "Tab not found in this restaurant, or tab is not open"),
            @ApiResponse(responseCode = "403", description = "Authenticated user lacks permission")
    })
    public ResponseEntity<Void> cancelAllCardCharges(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        cardChargeService.cancelPendingCharge(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/payments/{paymentId}/void")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Void a payment recorded by mistake", description = "Marks a single payment as VOIDED, keeping it in the tab's history for audit instead of overwriting it. Never reopens the tab or touches its tables, even if this leaves a CLOSED tab with an outstanding balance — by the time someone reaches this action the table may already be free or serving someone else entirely. A debt left behind this way is settled later through the payments-registration endpoint. Restricted to OWNER and MANAGER; a reason is required for audit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment voided"),
            @ApiResponse(responseCode = "400", description = "Tab or payment not found in this restaurant, or payment already voided"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<TabResponse> voidPayment(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @PathVariable UUID paymentId,
            @Valid @RequestBody VoidPaymentRequest request
    ) {
        // Routed through CardChargeService, not called on tabService directly: a payment linked to
        // a card charge needs a real gateway refund before the internal ledger is touched, but a
        // manual/cash/Pix payment just delegates straight through to TabService#voidPayment. See
        // CardChargeService#voidPayment's javadoc.
        return ResponseEntity.ok(cardChargeService.voidPayment(currentUser.getRestaurantId(), id, paymentId, currentUser.getId(), request));
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
