package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.PublicCreateReservationRequest;
import com.example.restaurant_saas.dto.response.ReservationResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicReservationService {

    private static final String CREATE_ACTION = "reservation-create";
    private static final String CANCEL_ACTION = "reservation-cancel";

    private final RestaurantRepository restaurantRepository;
    private final ReservationService reservationService;
    private final RateLimitService rateLimitService;

    @Value("${security.reservation-create-rate-limit.max-attempts}")
    private int createMaxAttempts;

    @Value("${security.reservation-create-rate-limit.window-minutes}")
    private long createWindowMinutes;

    @Value("${security.reservation-create-rate-limit.block-minutes}")
    private long createBlockMinutes;

    @Value("${security.reservation-cancel-rate-limit.max-attempts}")
    private int cancelMaxAttempts;

    @Value("${security.reservation-cancel-rate-limit.window-minutes}")
    private long cancelWindowMinutes;

    @Value("${security.reservation-cancel-rate-limit.block-minutes}")
    private long cancelBlockMinutes;

    @Transactional
    public ReservationResponse create(String slug, PublicCreateReservationRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkAllowed(CREATE_ACTION, httpRequest, request.getCustomerPhone());
        try {
            Restaurant restaurant = restaurantRepository.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

            // No explicit tableIds here by design: the public endpoint never accepts a client-supplied
            // table selection, so a customer can't pick a specific table id off the wire -- the system
            // always auto-assigns.
            return reservationService.createReservation(
                    restaurant.getId(),
                    request.getCustomerName(),
                    request.getCustomerPhone(),
                    request.getNote(),
                    request.getPartySize(),
                    request.getReservationTime(),
                    null,
                    null
            );
        } finally {
            // Counted whether this call succeeds or fails, same as forgot-password: otherwise someone
            // could probe table availability for free by repeatedly hitting the failure path only.
            rateLimitService.recordAttempt(CREATE_ACTION, httpRequest, request.getCustomerPhone(),
                    createMaxAttempts, createWindowMinutes, createBlockMinutes);
        }
    }

    @Transactional(readOnly = true)
    public ReservationResponse getByToken(String token) {
        return reservationService.getByToken(token);
    }

    @Transactional
    public ReservationResponse cancelByToken(String token, HttpServletRequest httpRequest) {
        rateLimitService.checkAllowed(CANCEL_ACTION, httpRequest, token);
        try {
            return reservationService.cancelByToken(token);
        } finally {
            rateLimitService.recordAttempt(CANCEL_ACTION, httpRequest, token,
                    cancelMaxAttempts, cancelWindowMinutes, cancelBlockMinutes);
        }
    }
}
