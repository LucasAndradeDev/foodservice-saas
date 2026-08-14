package com.example.warehouse.security;

import com.example.warehouse.config.WarehouseTenantContext;
import com.example.warehouse.repository.RestaurantLinkRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WarehouseJwtAuthenticationFilter extends OncePerRequestFilter {

    private final WarehouseJwtService warehouseJwtService;
    private final RestaurantLinkRepository restaurantLinkRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                final String jwt = authHeader.substring(7);
                final UUID restaurantLinkId = warehouseJwtService.extractRestaurantLinkId(jwt);

                if (restaurantLinkId != null && restaurantLinkRepository.existsById(restaurantLinkId)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            restaurantLinkId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT"))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    WarehouseTenantContext.setCurrentRestaurantLink(restaurantLinkId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            WarehouseTenantContext.clear();
        }
    }
}
