package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateReservationRequest;
import com.example.restaurant_saas.dto.response.ReservationResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.ReservationService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Manage table reservations. Write operations restricted to OWNER, MANAGER, WAITER and CASHIER.")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "List reservations for a day", description = "Lists the restaurant's reservations for the given date (defaults to today), ordered by reservation time. Any SCHEDULED reservation whose blocking window has already elapsed is flipped to NO_SHOW as a side effect, for display purposes only.")
    public ResponseEntity<List<ReservationResponse>> listReservations(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Date to list, defaults to today") @RequestParam(required = false) LocalDate date
    ) {
        return ResponseEntity.ok(reservationService.listReservations(currentUser.getRestaurantId(), date != null ? date : LocalDate.now()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Create a reservation", description = "Creates a reservation manually (e.g. by phone). If tableIds is omitted, the system auto-assigns the smallest table (or best pair of tables) with enough capacity; if provided, uses those specific tables instead, still validated for capacity and availability.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created"),
            @ApiResponse(responseCode = "400", description = "Validation error, or one or more selected tables not found/inactive/insufficient capacity"),
            @ApiResponse(responseCode = "403", description = "No table (or combination of up to two tables) is available for the requested party size and time")
    })
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationResponse response = reservationService.createReservation(
                currentUser.getRestaurantId(),
                request.getCustomerName(),
                request.getCustomerPhone(),
                request.getNote(),
                request.getPartySize(),
                request.getReservationTime(),
                currentUser.getId(),
                request.getTableIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Check in a reservation", description = "Marks the reservation as SEATED and opens a tab on its linked table(s), same as opening a tab manually. Allowed from SCHEDULED or NO_SHOW (a customer can still be seated after the no-show cutoff if the table is still available).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation checked in and tab opened"),
            @ApiResponse(responseCode = "400", description = "Reservation not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Reservation cannot be checked in from its current status, or its table(s) are no longer available")
    })
    public ResponseEntity<ReservationResponse> checkIn(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(reservationService.checkIn(currentUser.getRestaurantId(), id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','WAITER','CASHIER')")
    @Operation(summary = "Cancel a reservation", description = "Cancels a reservation, freeing its table(s) from the blocking window immediately. Allowed from SCHEDULED or NO_SHOW.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
            @ApiResponse(responseCode = "400", description = "Reservation not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Reservation cannot be cancelled from its current status")
    })
    public ResponseEntity<ReservationResponse> cancelReservation(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(reservationService.cancelByStaff(currentUser.getRestaurantId(), id));
    }
}
