package com.example.warehouse.service;

import com.example.warehouse.domain.entity.RestaurantLink;
import com.example.warehouse.dto.response.WarehouseSsoResponse;
import com.example.warehouse.repository.RestaurantLinkRepository;
import com.example.warehouse.security.HandoffClaims;
import com.example.warehouse.security.WarehouseHandoffVerifier;
import com.example.warehouse.security.WarehouseJwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseSsoService {

    private final WarehouseHandoffVerifier handoffVerifier;
    private final WarehouseJwtService warehouseJwtService;
    private final RestaurantLinkRepository restaurantLinkRepository;

    /**
     * Verifies the handoff token from Morá, upserts the RestaurantLink (auto-created on the
     * first handoff, refreshed on every later one - see docs/ARMAZEM_MORA.md), and mints
     * Armazém Morá's own session token for the frontend to use from here on.
     */
    @Transactional
    public WarehouseSsoResponse exchange(String handoffToken) {
        HandoffClaims claims = handoffVerifier.verify(handoffToken);

        RestaurantLink link = restaurantLinkRepository.findByMoraRestaurantId(claims.restaurantId())
                .orElseGet(() -> RestaurantLink.builder().moraRestaurantId(claims.restaurantId()).build());
        link.setName(claims.restaurantName());
        link.setApiKey(claims.apiKey());
        restaurantLinkRepository.save(link);

        String sessionToken = warehouseJwtService.generateToken(link.getId());

        return WarehouseSsoResponse.builder()
                .accessToken(sessionToken)
                .restaurantName(link.getName())
                .build();
    }
}
