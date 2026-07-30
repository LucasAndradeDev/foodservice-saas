package com.example.restaurant_saas.service;

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

    @Transactional(readOnly = true)
    public PublicFeedbackContextResponse getContext(String slug, UUID tabId) {
        Tab tab = findClosedTab(slug, tabId);
        return toResponse(tab);
    }

    @Transactional
    public PublicFeedbackContextResponse submitFeedback(String slug, UUID tabId, CreatePostMealFeedbackRequest request) {
        Tab tab = findClosedTab(slug, tabId);

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
    }

    private Tab findClosedTab(String slug, UUID tabId) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));
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
