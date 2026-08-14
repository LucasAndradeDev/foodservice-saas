package com.example.warehouse.service;

import com.example.warehouse.domain.entity.RestaurantLink;
import com.example.warehouse.dto.response.MoraProductResponse;
import com.example.warehouse.repository.RestaurantLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backs the Morá product picker used when the owner builds a ficha técnica (RecipeController) -
 * proxies GET /api/v1/internal/warehouse/products on Morá's side using the raw API key stored on
 * this restaurant's RestaurantLink (the same key MoraApiClient uses for the sales sync).
 */
@Service
@RequiredArgsConstructor
public class MoraProductService {

    private final RestaurantLinkRepository restaurantLinkRepository;
    private final MoraApiClient moraApiClient;

    @Transactional(readOnly = true)
    public List<MoraProductResponse> listProducts(UUID restaurantLinkId) {
        RestaurantLink link = restaurantLinkRepository.findById(restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant link not found."));

        return moraApiClient.fetchProducts(link.getApiKey()).stream()
                .map(product -> MoraProductResponse.builder()
                        .productId(product.productId())
                        .productName(product.productName())
                        .build())
                .toList();
    }
}
