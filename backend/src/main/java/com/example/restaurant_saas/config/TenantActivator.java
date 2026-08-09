package com.example.restaurant_saas.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets {@link TenantContext} AND immediately pushes {@code app.tenant_id} onto the current
 * transaction's JDBC connection via the live {@link EntityManager}.
 * <p>
 * Needed anywhere the tenant is discovered <em>inside</em> an already-open transaction (e.g.
 * resolving a restaurant by slug/email/token before doing protected work). {@link
 * TenantAwareDataSource} only stamps a connection once, at checkout time - Hibernate acquires one
 * physical connection per transaction and holds it for the whole transaction (the default
 * DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION connection-handling mode), so a TenantContext
 * change made *after* that first checkout would otherwise never reach Postgres: every later query
 * in the same transaction would still see whatever (or nothing) was set at checkout time.
 * <p>
 * For requests where TenantContext is already set *before* any repository access - the normal
 * JWT-authenticated path, set by JwtAuthenticationFilter before the controller even runs -
 * TenantAwareDataSource's checkout-time stamp is sufficient and this class isn't needed.
 */
@Component
public class TenantActivator {

    @PersistenceContext
    private EntityManager entityManager;

    public void activate(UUID restaurantId) {
        TenantContext.setCurrentTenant(restaurantId);
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, false)")
                .setParameter("tenantId", restaurantId.toString())
                .getSingleResult();
    }

    public void deactivate() {
        TenantContext.clear();
    }
}
