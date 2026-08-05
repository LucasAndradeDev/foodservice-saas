package com.example.restaurant_saas.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Enables the Hibernate {@code tenantFilter} (see the {@code @FilterDef} in
 * {@code domain.entity.package-info}) for the current request's persistence context, using
 * the restaurant id that {@code JwtAuthenticationFilter} already put in {@link TenantContext}.
 * <p>
 * Runs as an MVC interceptor (not a servlet filter) so it always executes after Spring
 * Security has authenticated the request and after Open-Session-In-View has bound the
 * request's EntityManager — both are plain servlet filters that complete before the
 * DispatcherServlet invokes interceptors.
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
        return true;
    }
}
