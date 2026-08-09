package com.example.restaurant_saas.support;

import com.example.restaurant_saas.config.TenantContext;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wraps a direct repository call (bypassing the HTTP/filter-chain path that normally populates
 * {@link TenantContext} via JwtAuthenticationFilter) so it runs with the tenant set - required
 * once RLS is live, since a save without app.tenant_id set fails the WITH CHECK policy on
 * tenant-filtered tables.
 */
public final class TenantTestSupport {

    private TenantTestSupport() {
    }

    public static <T> T withTenant(UUID restaurantId, Supplier<T> action) {
        TenantContext.setCurrentTenant(restaurantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    public static void withTenant(UUID restaurantId, Runnable action) {
        TenantContext.setCurrentTenant(restaurantId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
