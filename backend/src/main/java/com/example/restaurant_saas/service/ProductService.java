package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.example.restaurant_saas.dto.response.ProductResponse;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts(UUID restaurantId, UUID categoryId, String search, Boolean activeFilter) {
        return productRepository.findByRestaurantIdOrderByCategoryNameAscNameAsc(restaurantId).stream()
                .filter(product -> categoryId == null || product.getCategory().getId().equals(categoryId))
                .filter(product -> search == null || search.isBlank()
                        || product.getName().toLowerCase().contains(search.toLowerCase()))
                .filter(product -> activeFilter == null || activeFilter.equals(product.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID restaurantId, UUID productId) {
        return toResponse(findByIdAndRestaurant(restaurantId, productId));
    }

    @Transactional
    public ProductResponse createProduct(UUID restaurantId, CreateProductRequest request) {
        if (productRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new IllegalArgumentException("A product with this name already exists.");
        }

        Category category = findCategory(restaurantId, request.getCategoryId());

        Product product = Product.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .active(true)
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID restaurantId, UUID productId, UpdateProductRequest request) {
        Product product = findByIdAndRestaurant(restaurantId, productId);

        if (request.getName() != null) {
            if (productRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, request.getName(), productId)) {
                throw new IllegalArgumentException("A product with this name already exists.");
            }
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCategoryId() != null) {
            product.setCategory(findCategory(restaurantId, request.getCategoryId()));
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getSoldOut() != null) {
            product.setSoldOutAt(request.getSoldOut() ? OffsetDateTime.now() : null);
        }

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(UUID restaurantId, UUID productId) {
        Product product = findByIdAndRestaurant(restaurantId, productId);
        if (orderItemRepository.existsByProductId(productId)) {
            throw new IllegalStateException("Cannot delete a product that has already been ordered. Deactivate it instead.");
        }
        productRepository.delete(product);
    }

    private Category findCategory(UUID restaurantId, UUID categoryId) {
        return categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
    }

    private Product findByIdAndRestaurant(UUID restaurantId, UUID productId) {
        return productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .restaurantId(product.getRestaurant().getId())
                .categoryId(product.getCategory().getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .active(product.getActive())
                .soldOutToday(isSoldOutToday(product))
                .build();
    }

    private boolean isSoldOutToday(Product product) {
        return product.getSoldOutAt() != null
                && product.getSoldOutAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                    .equals(OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate());
    }
}
