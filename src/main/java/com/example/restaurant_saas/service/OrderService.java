package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TabRepository tabRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID restaurantId, UUID tabId) {
        return orderRepository.findByTabIdAndRestaurantId(tabId, restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID restaurantId, UUID orderId) {
        return toResponse(findByIdAndRestaurant(restaurantId, orderId));
    }

    @Transactional
    public OrderResponse createOrder(UUID restaurantId, UUID tabId, CreateOrderRequest request) {
        Tab tab = tabRepository.findByIdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalStateException("Tab is not open.");
        }

        Order order = Order.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .tab(tab)
                .build();

        List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> toOrderItem(restaurantId, order, itemRequest))
                .toList();
        order.setItems(items);

        return toResponse(orderRepository.save(order));
    }

    private OrderItem toOrderItem(UUID restaurantId, Order order, CreateOrderItemRequest itemRequest) {
        Product product = productRepository.findByIdAndRestaurantId(itemRequest.getProductId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Product " + product.getName() + " is not active.");
        }

        return OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(itemRequest.getQuantity())
                .unitPrice(product.getPrice())
                .observation(itemRequest.getObservation())
                .status(ItemStatus.PENDING)
                .build();
    }

    private Order findByIdAndRestaurant(UUID restaurantId, UUID orderId) {
        return orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .observation(item.getObservation())
                        .status(item.getStatus())
                        .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurant().getId())
                .tabId(order.getTab().getId())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .total(total)
                .build();
    }
}
