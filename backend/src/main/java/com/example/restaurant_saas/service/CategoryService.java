package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.UpdateCategoryRequest;
import com.example.restaurant_saas.dto.response.CategoryResponse;
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
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID restaurantId, Boolean activeFilter) {
        return categoryRepository.findByRestaurantId(restaurantId).stream()
                .filter(category -> activeFilter == null || activeFilter.equals(category.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID restaurantId, UUID categoryId) {
        return toResponse(findByIdAndRestaurant(restaurantId, categoryId));
    }

    @Transactional
    public CategoryResponse createCategory(UUID restaurantId, CreateCategoryRequest request) {
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new IllegalArgumentException("A category with this name already exists.");
        }

        Category category = Category.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .name(request.getName())
                .active(true)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID restaurantId, UUID categoryId, UpdateCategoryRequest request) {
        Category category = findByIdAndRestaurant(restaurantId, categoryId);

        if (request.getName() != null) {
            if (categoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, request.getName(), categoryId)) {
                throw new IllegalArgumentException("A category with this name already exists.");
            }
            category.setName(request.getName());
        }
        if (Boolean.FALSE.equals(request.getActive()) && productRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new IllegalStateException("Cannot deactivate a category that still has active products.");
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    private Category findByIdAndRestaurant(UUID restaurantId, UUID categoryId) {
        return categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .restaurantId(category.getRestaurant().getId())
                .name(category.getName())
                .active(category.getActive())
                .build();
    }
}
