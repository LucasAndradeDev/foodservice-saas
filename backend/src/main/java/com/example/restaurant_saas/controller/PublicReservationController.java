package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.PublicCreateReservationRequest;
import com.example.restaurant_saas.dto.response.ReservationResponse;
import com.example.restaurant_saas.service.PublicReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicReservationController {

    private final PublicReservationService publicReservationService;

    @PostMapping("/menu/{slug}/reservations")
    @Operation(summary = "Create a reservation", description = "Creates a reservation without authentication. The system always auto-assigns the smallest table (or best pair of tables) with enough capacity for the requested party size and time -- there is no way to request a specific table through this endpoint. Returns an access token used to view or cancel the reservation later, since there is no customer login. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created"),
            @ApiResponse(responseCode = "400", description = "Validation error, or no restaurant found for this slug"),
            @ApiResponse(responseCode = "403", description = "No table (or combination of up to two tables) is available for the requested party size and time"),
            @ApiResponse(responseCode = "429", description = "Too many reservation attempts for this phone number")
    })
    public ResponseEntity<ReservationResponse> create(
            @PathVariable String slug,
            @Valid @RequestBody PublicCreateReservationRequest request,
            HttpServletRequest httpRequest
    ) {
        ReservationResponse response = publicReservationService.create(slug, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/reservations/{token}")
    @Operation(summary = "Get a reservation by its access token", description = "Returns the reservation identified by the given access token, without authentication. The token itself is the only credential -- there is no restaurant slug in this path.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation returned"),
            @ApiResponse(responseCode = "400", description = "No reservation found for this token")
    })
    public ResponseEntity<ReservationResponse> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(publicReservationService.getByToken(token));
    }

    @DeleteMapping("/reservations/{token}")
    @Operation(summary = "Cancel a reservation by its access token", description = "Cancels the reservation identified by the given access token, without authentication. Allowed only while the reservation is still SCHEDULED or NO_SHOW.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
            @ApiResponse(responseCode = "400", description = "No reservation found for this token"),
            @ApiResponse(responseCode = "403", description = "Reservation cannot be cancelled from its current status"),
            @ApiResponse(responseCode = "429", description = "Too many cancellation attempts for this token")
    })
    public ResponseEntity<ReservationResponse> cancelByToken(@PathVariable String token, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(publicReservationService.cancelByToken(token, httpRequest));
    }
}
