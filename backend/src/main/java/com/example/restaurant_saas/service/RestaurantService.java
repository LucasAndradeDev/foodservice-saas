package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.UpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public RestaurantResponse getMyRestaurant(UUID restaurantId) {
        Restaurant restaurant = findById(restaurantId);
        return toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateMyRestaurant(UUID restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = findById(restaurantId);

        if (request.getTradeName() != null) {
            restaurant.setTradeName(request.getTradeName());
        }
        if (request.getSlug() != null) {
            if (!request.getSlug().isBlank() && restaurantRepository.existsBySlugAndIdNot(request.getSlug(), restaurantId)) {
                throw new IllegalArgumentException("Slug already in use.");
            }
            restaurant.setSlug(request.getSlug());
        }
        if (request.getLogo() != null) {
            restaurant.setLogo(request.getLogo());
        }
        if (request.getTableCount() != null) {
            restaurant.setTableCount(request.getTableCount());
        }
        if (request.getPhone() != null) {
            restaurant.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            restaurant.setAddress(request.getAddress());
        }
        if (request.getCnpj() != null) {
            if (!request.getCnpj().isBlank() && restaurantRepository.existsByCnpjAndIdNot(request.getCnpj(), restaurantId)) {
                throw new IllegalArgumentException("CNPJ already registered.");
            }
            restaurant.setCnpj(request.getCnpj());
        }
        if (request.getAutoPrintKitchenTickets() != null) {
            restaurant.setAutoPrintKitchenTickets(request.getAutoPrintKitchenTickets());
        }
        if (request.getKitchenWarningThresholdMinutes() != null) {
            restaurant.setKitchenWarningThresholdMinutes(request.getKitchenWarningThresholdMinutes());
        }
        if (request.getKitchenCriticalThresholdMinutes() != null) {
            restaurant.setKitchenCriticalThresholdMinutes(request.getKitchenCriticalThresholdMinutes());
        }
        if (restaurant.getKitchenCriticalThresholdMinutes() <= restaurant.getKitchenWarningThresholdMinutes()) {
            throw new IllegalArgumentException("Critical threshold must be greater than the warning threshold.");
        }
        if (request.getTableForgottenWarningThresholdMinutes() != null) {
            restaurant.setTableForgottenWarningThresholdMinutes(request.getTableForgottenWarningThresholdMinutes());
        }
        if (request.getTableForgottenCriticalThresholdMinutes() != null) {
            restaurant.setTableForgottenCriticalThresholdMinutes(request.getTableForgottenCriticalThresholdMinutes());
        }
        if (restaurant.getTableForgottenCriticalThresholdMinutes() <= restaurant.getTableForgottenWarningThresholdMinutes()) {
            throw new IllegalArgumentException("Critical threshold must be greater than the warning threshold.");
        }
        if (request.getServiceChargeEnabled() != null) {
            restaurant.setServiceChargeEnabled(request.getServiceChargeEnabled());
        }
        if (request.getServiceChargePercentage() != null) {
            restaurant.setServiceChargePercentage(request.getServiceChargePercentage());
        }

        restaurant = restaurantRepository.save(restaurant);
        return toResponse(restaurant);
    }

    private Restaurant findById(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .tradeName(restaurant.getTradeName())
                .slug(restaurant.getSlug())
                .cnpj(restaurant.getCnpj())
                .phone(restaurant.getPhone())
                .address(restaurant.getAddress())
                .logo(restaurant.getLogo())
                .tableCount(restaurant.getTableCount())
                .active(restaurant.getActive())
                .autoPrintKitchenTickets(restaurant.getAutoPrintKitchenTickets())
                .kitchenWarningThresholdMinutes(restaurant.getKitchenWarningThresholdMinutes())
                .kitchenCriticalThresholdMinutes(restaurant.getKitchenCriticalThresholdMinutes())
                .tableForgottenWarningThresholdMinutes(restaurant.getTableForgottenWarningThresholdMinutes())
                .tableForgottenCriticalThresholdMinutes(restaurant.getTableForgottenCriticalThresholdMinutes())
                .serviceChargeEnabled(restaurant.getServiceChargeEnabled())
                .serviceChargePercentage(restaurant.getServiceChargePercentage())
                .build();
    }
}
