package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.enums.ProductType;
import com.example.restaurant_saas.dto.response.WarehouseProductResponse;
import com.example.restaurant_saas.dto.response.WarehouseSaleItemResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Backs the /api/v1/internal/warehouse/** endpoints (see WarehouseSalesController and
 * WarehouseProductsController) - the pull side of Armazém Morá's stock sync and recipe picker
 * (docs/ARMAZEM_MORA.md). Tenant is only known once the caller's API key resolves to a restaurant
 * (WarehouseIntegrationService#resolveRestaurantId), so this runs each query under an explicitly
 * activated tenant the same way PixChargeService#handleWebhook does.
 */
@Service
@RequiredArgsConstructor
public class WarehouseSalesService {

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final TenantActivator tenantActivator;

    @Transactional
    public List<WarehouseSaleItemResponse> findDeliveredSalesSince(UUID restaurantId, OffsetDateTime since) {
        tenantActivator.activate(restaurantId);
        try {
            return orderItemRepository.findDeliveredLeafItemsSince(restaurantId, since).stream()
                    .map(this::toResponse)
                    .toList();
        } finally {
            tenantActivator.deactivate();
        }
    }

    private WarehouseSaleItemResponse toResponse(OrderItem item) {
        return WarehouseSaleItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .deliveredAt(item.getDeliveredAt())
                .build();
    }

    /**
     * Combos are excluded: sales are reported with their components already exploded (see
     * findDeliveredSalesSince), so a recipe tied to a combo's productId would never match a sale.
     */
    @Transactional
    public List<WarehouseProductResponse> findActiveSimpleProducts(UUID restaurantId) {
        tenantActivator.activate(restaurantId);
        try {
            return productRepository.findByRestaurantIdAndActiveTrueAndTypeOrderByNameAsc(restaurantId, ProductType.SIMPLE).stream()
                    .map(product -> WarehouseProductResponse.builder()
                            .productId(product.getId())
                            .productName(product.getName())
                            .build())
                    .toList();
        } finally {
            tenantActivator.deactivate();
        }
    }
}
