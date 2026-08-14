package com.example.warehouse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Calls Morá's GET /api/v1/internal/warehouse/sales, the pull side of the stock sync (see
 * docs/ARMAZEM_MORA.md). Authenticated per-restaurant by the raw integration API key stored on
 * that RestaurantLink - not a session token, since there's no logged-in user behind a sync job.
 */
@Component
public class MoraApiClient {

    private static final String API_KEY_HEADER = "X-Warehouse-Api-Key";

    private final RestClient restClient;

    public MoraApiClient(@Value("${mora.internal-base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record SaleItem(UUID productId, String productName, Integer quantity, OffsetDateTime deliveredAt) {
    }

    public record ProductItem(UUID productId, String productName) {
    }

    public List<SaleItem> fetchSalesSince(String apiKey, OffsetDateTime since) {
        SaleItem[] items = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/warehouse/sales")
                        .queryParam("since", since.toString())
                        .build())
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(SaleItem[].class);

        return items != null ? List.of(items) : List.of();
    }

    public List<ProductItem> fetchProducts(String apiKey) {
        ProductItem[] items = restClient.get()
                .uri("/api/v1/internal/warehouse/products")
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(ProductItem[].class);

        return items != null ? List.of(items) : List.of();
    }
}
