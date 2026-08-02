package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CashWithdrawalRequest;
import com.example.restaurant_saas.dto.request.CloseCashRegisterRequest;
import com.example.restaurant_saas.dto.request.OpenCashRegisterRequest;
import com.example.restaurant_saas.dto.response.CashRegisterSessionResponse;
import com.example.restaurant_saas.dto.response.CashWithdrawalResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CashRegisterService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/v1/cash-register")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
@Tag(name = "Cash Register", description = "Cash register session opening/closing and cash withdrawals. Restricted to OWNER, MANAGER and CASHIER.")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    @GetMapping("/current")
    @Operation(summary = "Get current cash register session", description = "Returns the open session with its live expected cash amount, or 204 No Content if the cash register is closed.")
    public ResponseEntity<CashRegisterSessionResponse> getCurrentSession(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return cashRegisterService.getCurrentSession(currentUser.getRestaurantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions")
    @Operation(summary = "List cash register session history", description = "Lists the most recent cash register sessions, newest first.")
    public ResponseEntity<List<CashRegisterSessionResponse>> listHistory(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(cashRegisterService.listHistory(currentUser.getRestaurantId()));
    }

    @PostMapping("/open")
    @Operation(summary = "Open the cash register", description = "Opens a new cash register session with an opening cash amount. Fails if a session is already open.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session opened"),
            @ApiResponse(responseCode = "400", description = "A session is already open"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER, MANAGER or CASHIER")
    })
    public ResponseEntity<CashRegisterSessionResponse> openSession(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody OpenCashRegisterRequest request
    ) {
        CashRegisterSessionResponse response = cashRegisterService.openSession(currentUser.getRestaurantId(), currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/close")
    @Operation(summary = "Close the cash register", description = "Closes the open session with the physically counted cash amount. Requires closing notes when the counted amount differs from the expected amount.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session closed"),
            @ApiResponse(responseCode = "400", description = "No open session, or closing notes missing for a divergent amount"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER, MANAGER or CASHIER")
    })
    public ResponseEntity<CashRegisterSessionResponse> closeSession(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CloseCashRegisterRequest request
    ) {
        return ResponseEntity.ok(cashRegisterService.closeSession(currentUser.getRestaurantId(), currentUser.getId(), request));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Register a cash withdrawal (sangria)", description = "Registers a cash withdrawal from the open session, with a mandatory reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Withdrawal registered"),
            @ApiResponse(responseCode = "400", description = "No open session, or validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER, MANAGER or CASHIER")
    })
    public ResponseEntity<CashWithdrawalResponse> addWithdrawal(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CashWithdrawalRequest request
    ) {
        CashWithdrawalResponse response = cashRegisterService.addWithdrawal(currentUser.getRestaurantId(), currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
