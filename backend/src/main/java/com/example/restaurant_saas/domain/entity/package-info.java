/**
 * Shared Hibernate {@code @Filter} definition for tenant isolation.
 * <p>
 * Enabled once per request (see {@code TenantFilterInterceptor}) using the restaurant id
 * from the authenticated JWT. Entities with a direct {@code restaurant_id} column apply it
 * via {@code @Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")}.
 * This is a defense-in-depth safety net on top of the explicit
 * {@code findByIdAndRestaurantId}-style repository queries, not a replacement for them:
 * it does not cover native SQL queries, which bypass Hibernate filters entirely.
 */
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
package com.example.restaurant_saas.domain.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;
