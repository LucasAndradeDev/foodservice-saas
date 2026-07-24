package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.response.PublicMenuCategoryResponse;
import com.example.restaurant_saas.dto.response.PublicMenuProductResponse;
import com.example.restaurant_saas.dto.response.PublicMenuResponse;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PublicMenuResponse getPublicMenu(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        List<Product> activeProducts = productRepository.findByRestaurantIdAndActiveTrue(restaurant.getId());

        List<PublicMenuCategoryResponse> categories = categoryRepository
                .findByRestaurantIdAndActiveTrue(restaurant.getId())
                .stream()
                .map(category -> toCategoryResponse(category, activeProducts))
                .filter(category -> !category.getProducts().isEmpty())
                .toList();

        String restaurantName = restaurant.getTradeName() != null ? restaurant.getTradeName() : restaurant.getName();

        return PublicMenuResponse.builder()
                .restaurantName(restaurantName)
                .logo(restaurant.getLogo())
                .primaryColor(restaurant.getPrimaryColor())
                .categories(categories)
                .build();
    }

    private PublicMenuCategoryResponse toCategoryResponse(Category category, List<Product> activeProducts) {
        List<PublicMenuProductResponse> products = activeProducts.stream()
                .filter(product -> product.getCategory().getId().equals(category.getId()))
                .map(this::toProductResponse)
                .toList();

        return PublicMenuCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .products(products)
                .build();
    }

    private PublicMenuProductResponse toProductResponse(Product product) {
        return PublicMenuProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .build();
    }
}
