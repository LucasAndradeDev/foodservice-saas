package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.AdminUpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Convention: this service (and AdminController) may only query RestaurantRepository. Platform-admin
// requests never set TenantContext (see JwtAuthenticationFilter), so any other @Filter-annotated
// entity queried here would come back completely unscoped across every tenant.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;

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
}
