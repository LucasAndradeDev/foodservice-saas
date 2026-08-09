package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.PostMealFeedback;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.CreatePostMealFeedbackRequest;
import com.example.restaurant_saas.dto.response.PublicFeedbackContextResponse;
import com.example.restaurant_saas.repository.PostMealFeedbackRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicFeedbackService {

    private final RestaurantRepository restaurantRepository;
    private final TabRepository tabRepository;
    private final PostMealFeedbackRepository feedbackRepository;
    private final TenantActivator tenantActivator;

    @Transactional(readOnly = true)
    public PublicFeedbackContextResponse getContext(String slug, UUID tabId) {
        Restaurant restaurant = resolveRestaurant(slug);
        tenantActivator.activate(restaurant.getId());
        try {
            Tab tab = findClosedTab(restaurant, tabId);
            return toResponse(tab);
        } finally {
            tenantActivator.deactivate();
        }
    }

    @Transactional
    public PublicFeedbackContextResponse submitFeedback(String slug, UUID tabId, CreatePostMealFeedbackRequest request) {
        Restaurant restaurant = resolveRestaurant(slug);
        tenantActivator.activate(restaurant.getId());
        try {
            Tab tab = findClosedTab(restaurant, tabId);

            if (feedbackRepository.existsByTabId(tabId)) {
                throw new IllegalStateException("Feedback has already been submitted for this tab.");
            }

            PostMealFeedback feedback = PostMealFeedback.builder()
                    .restaurant(tab.getRestaurant())
                    .tab(tab)
                    .rating(request.getRating())
                    .comment(request.getComment())
                    .build();
            feedbackRepository.save(feedback);

            return toResponse(tab);
        } finally {
            tenantActivator.deactivate();
        }
    }

    private Restaurant resolveRestaurant(String slug) {
        return restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));
    }

    private Tab findClosedTab(Restaurant restaurant, UUID tabId) {
        Tab tab = tabRepository.findByIdAndRestaurantId(tabId, restaurant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.CLOSED) {
            throw new IllegalArgumentException("Tab is not closed yet.");
        }
        return tab;
    }

    private PublicFeedbackContextResponse toResponse(Tab tab) {
        Restaurant restaurant = tab.getRestaurant();
        String restaurantName = restaurant.getTradeName() != null ? restaurant.getTradeName() : restaurant.getName();

        return PublicFeedbackContextResponse.builder()
                .restaurantName(restaurantName)
                .logo(restaurant.getLogo())
                .tableNumbers(tab.getTables().stream().map(RestaurantTable::getNumber).toList())
                .alreadySubmitted(feedbackRepository.existsByTabId(tab.getId()))
                .build();
    }
}
