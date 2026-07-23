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
        if (request.getLogo() != null) {
            restaurant.setLogo(request.getLogo());
        }
        if (request.getPrimaryColor() != null) {
            restaurant.setPrimaryColor(request.getPrimaryColor());
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
                .cnpj(restaurant.getCnpj())
                .phone(restaurant.getPhone())
                .address(restaurant.getAddress())
                .logo(restaurant.getLogo())
                .primaryColor(restaurant.getPrimaryColor())
                .tableCount(restaurant.getTableCount())
                .active(restaurant.getActive())
                .build();
    }
}
