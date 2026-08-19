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
        return categoryRepository.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
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
                .bannerImageUrl(blankToNull(request.getBannerImageUrl()))
                .icon(blankToNull(request.getIcon()))
                .active(true)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public Category findOrCreateCategory(UUID restaurantId, String name) {
        return categoryRepository.findByRestaurantIdAndNameIgnoreCase(restaurantId, name)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .name(name)
                        .active(true)
                        .build()));
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
        if (request.getBannerImageUrl() != null) {
            category.setBannerImageUrl(blankToNull(request.getBannerImageUrl()));
        }
        if (request.getIcon() != null) {
            category.setIcon(blankToNull(request.getIcon()));
        }
        if (Boolean.FALSE.equals(request.getActive()) && productRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new IllegalStateException("Cannot deactivate a category that still has active products.");
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID restaurantId, UUID categoryId) {
        Category category = findByIdAndRestaurant(restaurantId, categoryId);
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new IllegalStateException("Cannot delete a category that has products. Remove or move them first.");
        }
        categoryRepository.delete(category);
    }

    private Category findByIdAndRestaurant(UUID restaurantId, UUID categoryId) {
        return categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .restaurantId(category.getRestaurant().getId())
                .name(category.getName())
                .bannerImageUrl(category.getBannerImageUrl())
                .icon(category.getIcon())
                .active(category.getActive())
                .build();
    }
}
