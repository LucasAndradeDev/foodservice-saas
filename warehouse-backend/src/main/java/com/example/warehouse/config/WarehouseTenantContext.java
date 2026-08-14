package com.example.warehouse.config;

import java.util.UUID;

/**
 * Current RestaurantLink for this request, set by JwtAuthenticationFilter after validating
 * the session token. Same shape as Morá's TenantContext - every Armazém entity (Ingredient,
 * Supplier, ...) is scoped by restaurantLinkId, and this is how services reach it without
 * threading the id through every method signature.
 */
public final class WarehouseTenantContext {

    private static final ThreadLocal<UUID> CURRENT_RESTAURANT_LINK = new ThreadLocal<>();

    private WarehouseTenantContext() {
    }

    public static void setCurrentRestaurantLink(UUID restaurantLinkId) {
        CURRENT_RESTAURANT_LINK.set(restaurantLinkId);
    }

    public static UUID getCurrentRestaurantLink() {
        return CURRENT_RESTAURANT_LINK.get();
    }

    public static void clear() {
        CURRENT_RESTAURANT_LINK.remove();
    }
}
