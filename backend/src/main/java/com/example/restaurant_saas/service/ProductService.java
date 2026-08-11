package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductAvailabilityWindow;
import com.example.restaurant_saas.domain.entity.ProductImage;
import com.example.restaurant_saas.domain.enums.ProductType;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.example.restaurant_saas.dto.response.ProductResponse;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.ComboItemRepository;
import com.example.restaurant_saas.repository.ComboSlotOptionRepository;
import com.example.restaurant_saas.repository.ComboSlotRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.ProductAvailabilityWindowRepository;
import com.example.restaurant_saas.repository.ProductImageRepository;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductAvailabilityWindowRepository availabilityWindowRepository;
    private final ProductModifierGroupRepository modifierGroupRepository;
    private final ComboItemRepository comboItemRepository;
    private final ComboSlotRepository comboSlotRepository;
    private final ComboSlotOptionRepository comboSlotOptionRepository;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts(UUID restaurantId, UUID categoryId, String search, Boolean activeFilter, ProductType typeFilter) {
        List<Product> products = productRepository.findByRestaurantIdOrderByCategoryNameAscNameAsc(restaurantId).stream()
                .filter(product -> categoryId == null || product.getCategory().getId().equals(categoryId))
                .filter(product -> search == null || search.isBlank()
                        || product.getName().toLowerCase().contains(search.toLowerCase()))
                .filter(product -> activeFilter == null || activeFilter.equals(product.getActive()))
                .filter(product -> typeFilter == null || product.getType() == typeFilter)
                .toList();

        Set<UUID> productIdsWithModifiers = products.isEmpty()
                ? Set.of()
                : modifierGroupRepository
                        .findByRestaurantIdAndProductIdInOrderByCreatedAtAsc(restaurantId, products.stream().map(Product::getId).toList())
                        .stream()
                        .map(group -> group.getProduct().getId())
                        .collect(Collectors.toSet());

        Map<UUID, List<String>> galleryImagesByProduct = products.isEmpty()
                ? Map.of()
                : productImageRepository
                        .findByProductIdInOrderBySortOrderAsc(products.stream().map(Product::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                image -> image.getProduct().getId(),
                                Collectors.mapping(ProductImage::getImageUrl, Collectors.toList())));

        return products.stream()
                .map(product -> toResponse(
                        product,
                        productIdsWithModifiers.contains(product.getId()),
                        galleryImagesByProduct.getOrDefault(product.getId(), List.of())))
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
                .costPrice(request.getCostPrice())
                .active(true)
                .featured(Boolean.TRUE.equals(request.getFeatured()))
                .type(request.getType() != null ? request.getType() : ProductType.SIMPLE)
                .build();

        Product saved = productRepository.save(product);
        if (request.getGalleryImageUrls() != null) {
            saveGalleryImages(restaurantId, saved, request.getGalleryImageUrls());
        }

        return toResponse(saved);
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
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
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
        if (request.getFeatured() != null) {
            product.setFeatured(request.getFeatured());
        }
        Product saved = productRepository.save(product);
        if (request.getGalleryImageUrls() != null) {
            saveGalleryImages(restaurantId, saved, request.getGalleryImageUrls());
        }

        return toResponse(saved);
    }

    private void saveGalleryImages(UUID restaurantId, Product product, List<String> imageUrls) {
        productImageRepository.deleteByProductId(product.getId());
        var restaurant = restaurantRepository.getReferenceById(restaurantId);
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(ProductImage.builder()
                    .restaurant(restaurant)
                    .product(product)
                    .imageUrl(imageUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
        productImageRepository.saveAll(images);
    }

    @Transactional
    public void deleteProduct(UUID restaurantId, UUID productId) {
        Product product = findByIdAndRestaurant(restaurantId, productId);
        if (orderItemRepository.existsByProductId(productId)) {
            throw new IllegalStateException("Cannot delete a product that has already been ordered. Deactivate it instead.");
        }
        if (comboItemRepository.existsByComponentProductId(productId) || comboSlotOptionRepository.existsByProductId(productId)) {
            throw new IllegalStateException("Cannot delete a product that is used as a component of a combo. Remove it from the combo first.");
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
        boolean hasModifierGroups = !modifierGroupRepository
                .findByRestaurantIdAndProductIdInOrderByCreatedAtAsc(product.getRestaurant().getId(), List.of(product.getId()))
                .isEmpty();
        List<String> galleryImageUrls = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream()
                .map(ProductImage::getImageUrl)
                .toList();
        return toResponse(product, hasModifierGroups, galleryImageUrls);
    }

    private ProductResponse toResponse(Product product, boolean hasModifierGroups, List<String> galleryImageUrls) {
        return ProductResponse.builder()
                .id(product.getId())
                .restaurantId(product.getRestaurant().getId())
                .categoryId(product.getCategory().getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .galleryImageUrls(galleryImageUrls)
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .active(product.getActive())
                .soldOutToday(isSoldOutToday(product))
                .featured(product.getFeatured())
                .availableNow(isAvailableNow(product))
                .hasModifierGroups(hasModifierGroups)
                .type(product.getType())
                .discountPercentage(product.getDiscountPercentage())
                .build();
    }

    private boolean isSoldOutToday(Product product) {
        return product.getSoldOutAt() != null
                && product.getSoldOutAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                    .equals(OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate());
    }

    private boolean isAvailableNow(Product product) {
        List<ProductAvailabilityWindow> windows = availabilityWindowRepository
                .findByProductIdAndRestaurantIdOrderByCreatedAtAsc(product.getId(), product.getRestaurant().getId());
        if (windows.isEmpty()) {
            return true;
        }

        ZonedDateTime now = OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        return windows.stream().anyMatch(window ->
                (window.getDayOfWeek() == null || window.getDayOfWeek() == today)
                        && !timeNow.isBefore(window.getStartTime())
                        && !timeNow.isAfter(window.getEndTime()));
    }
}
