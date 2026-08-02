package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicOrderService {

    private final RestaurantRepository restaurantRepository;
    private final TabService tabService;
    private final OrderService orderService;

    @Transactional
    public OrderResponse createOrder(String slug, UUID tableId, CreateOrderRequest request) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        Tab tab = tabService.openOrGetTabForTable(restaurant.getId(), tableId);

        return orderService.createOrder(restaurant.getId(), tab.getId(), request, null);
    }
}
