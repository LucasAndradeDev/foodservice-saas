package com.example.restaurant_saas.security;

import com.example.restaurant_saas.config.TenantContext;
import com.example.restaurant_saas.domain.entity.AdminCredentials;
import com.example.restaurant_saas.repository.AdminCredentialsRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RestaurantRepository restaurantRepository;
    private final AdminCredentialsRepository adminCredentialsRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);

            try {
                final String subject = jwtService.extractEmail(jwt);

                if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Platform-admin tokens (role=ROLE_SUPER_ADMIN) never correspond to a row in
                    // `users` — branch here, before any UserDetailsService/DB lookup, so an admin
                    // token can never trigger a tenant user lookup (or vice versa).
                    if (JwtService.SUPER_ADMIN_ROLE.equals(jwtService.extractRole(jwt))) {
                        // The DB row (AdminCredentials), not the ADMIN_USERNAME env var, is the
                        // source of truth after first boot (see AdminCredentialsSeeder) - tokens
                        // are minted with whatever username is in the DB at issuance time
                        // (AdminAuthService#buildAuthResponse), so validating against the env var
                        // here would lock the admin out the moment it drifts from the DB (e.g. a
                        // redeploy with the env var left unchanged after an admin renamed the
                        // account).
                        String currentAdminUsername = adminCredentialsRepository.findFirstByOrderByCreatedAtAsc()
                                .map(AdminCredentials::getUsername)
                                .orElse(null);
                        if (currentAdminUsername != null && jwtService.isAdminTokenValid(jwt, currentAdminUsername)) {
                            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(JwtService.SUPER_ADMIN_ROLE));
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    subject,
                                    null,
                                    authorities
                            );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            // Deliberately never call TenantContext.setCurrentTenant(...) here: the admin
                            // must see every restaurant, and Restaurant itself carries no tenant @Filter.
                        }
                    } else {
                        UserDetails userDetails = this.userDetailsService.loadUserByUsername(subject);

                        if (jwtService.isTokenValid(jwt, userDetails.getUsername()) && userDetails.isEnabled()) {
                            final UUID restaurantId = jwtService.extractRestaurantId(jwt) != null
                                    ? jwtService.extractRestaurantId(jwt)
                                    : (userDetails instanceof UserDetailsImpl customUser ? customUser.getRestaurantId() : null);

                            // A restaurant blocked for non-payment (Restaurant.active = false) must lose
                            // access on its very next request, not just at its next login/refresh — access
                            // tokens live for 24h and aren't otherwise re-checked against the database.
                            // Likewise, a deactivated user (userDetails.isEnabled() above) must lose access
                            // on its very next request, not just at its next login/refresh.
                            if (restaurantId == null || restaurantRepository.existsByIdAndActiveTrue(restaurantId)) {
                                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authToken);

                                if (restaurantId != null) {
                                    TenantContext.setCurrentTenant(restaurantId);
                                }
                            }
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
                // Missing, expired or malformed token — or a token whose subject no longer resolves
                // to a user (e.g. UsernameNotFoundException, an AuthenticationException subtype):
                // proceed without authenticating. SecurityConfig's authorization rules take care of
                // rejecting the request.
            }
            filterChain.doFilter(request, response);
        } finally {
            // Clear the tenant context after the request
            TenantContext.clear();
        }
    }
}
