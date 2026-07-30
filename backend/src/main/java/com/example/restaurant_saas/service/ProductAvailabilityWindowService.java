package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductAvailabilityWindow;
import com.example.restaurant_saas.dto.request.AvailabilityWindowRequest;
import com.example.restaurant_saas.dto.response.AvailabilityWindowResponse;
import com.example.restaurant_saas.repository.ProductAvailabilityWindowRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductAvailabilityWindowService {

    private final ProductAvailabilityWindowRepository windowRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<AvailabilityWindowResponse> listWindows(UUID restaurantId, UUID productId) {
        findProduct(restaurantId, productId);
        return windowRepository.findByProductIdAndRestaurantIdOrderByCreatedAtAsc(productId, restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AvailabilityWindowResponse createWindow(UUID restaurantId, UUID productId, AvailabilityWindowRequest request) {
        validateRange(request.getStartTime(), request.getEndTime());
        Product product = findProduct(restaurantId, productId);

        ProductAvailabilityWindow window = ProductAvailabilityWindow.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .product(product)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();

        return toResponse(windowRepository.save(window));
    }

    @Transactional
    public AvailabilityWindowResponse updateWindow(UUID restaurantId, UUID productId, UUID windowId, AvailabilityWindowRequest request) {
        validateRange(request.getStartTime(), request.getEndTime());
        ProductAvailabilityWindow window = findWindow(restaurantId, productId, windowId);

        window.setDayOfWeek(request.getDayOfWeek());
        window.setStartTime(request.getStartTime());
        window.setEndTime(request.getEndTime());

        return toResponse(windowRepository.save(window));
    }

    @Transactional
    public void deleteWindow(UUID restaurantId, UUID productId, UUID windowId) {
        ProductAvailabilityWindow window = findWindow(restaurantId, productId, windowId);
        windowRepository.delete(window);
    }

    private void validateRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }
    }

    private Product findProduct(UUID restaurantId, UUID productId) {
        return productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
    }

    private ProductAvailabilityWindow findWindow(UUID restaurantId, UUID productId, UUID windowId) {
        return windowRepository.findByIdAndProductIdAndRestaurantId(windowId, productId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Availability window not found."));
    }

    private AvailabilityWindowResponse toResponse(ProductAvailabilityWindow window) {
        return AvailabilityWindowResponse.builder()
                .id(window.getId())
                .dayOfWeek(window.getDayOfWeek())
                .startTime(window.getStartTime())
                .endTime(window.getEndTime())
                .build();
    }
}
