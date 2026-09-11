package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.AdminUpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Convention: this service (and AdminController) may only query RestaurantRepository directly -
// platform-admin requests never set TenantContext (see JwtAuthenticationFilter), so any other
// @Filter/RLS-scoped entity queried here would come back completely empty or unscoped. The one
// exception is UserRepository#findOwnerEmailBypassingRls below, which goes through the same
// SECURITY DEFINER bypass function login/forgot-password already rely on (see V52, V79).
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<RestaurantResponse> listAll() {
        return restaurantRepository.findAll().stream()
                .map(restaurantService::toResponse)
                .toList();
    }

    @Transactional
    public RestaurantResponse updateStatus(UUID restaurantId, AdminUpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));

        log.info("Admin panel: restaurant {} active {} -> {}, paymentDueDate {} -> {}",
                restaurantId, restaurant.getActive(), request.getActive(),
                restaurant.getPaymentDueDate(), request.getPaymentDueDate());

        restaurant.setActive(request.getActive());
        restaurant.setPaymentDueDate(request.getPaymentDueDate());
        restaurant = restaurantRepository.save(restaurant);

        return restaurantService.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse approve(UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));

        if (Boolean.TRUE.equals(restaurant.getApproved())) {
            return restaurantService.toResponse(restaurant);
        }

        log.info("Admin panel: restaurant {} approved", restaurantId);
        restaurant.setApproved(true);
        restaurant = restaurantRepository.save(restaurant);

        String ownerEmail = userRepository.findOwnerEmailBypassingRls(restaurantId);
        if (ownerEmail != null) {
            try {
                emailService.sendAccountApprovedEmail(ownerEmail, frontendUrl + "/login");
            } catch (RuntimeException ex) {
                log.error("Failed to send restaurant approval email", ex);
            }
        }

        return restaurantService.toResponse(restaurant);
    }
}
