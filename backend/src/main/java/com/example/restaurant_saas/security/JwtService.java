package com.example.restaurant_saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${api.jwt.secret}")
    private String secretKey;

    @Value("${api.jwt.expiration-ms}")
    private long jwtExpiration;

    @Value("${api.jwt.admin-expiration-ms}")
    private long adminJwtExpiration;

    public static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractRestaurantId(String token) {
        String restaurantIdStr = extractClaim(token, claims -> claims.get("restaurantId", String.class));
        return restaurantIdStr != null ? UUID.fromString(restaurantIdStr) : null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetailsImpl userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userDetails.getId().toString());
        extraClaims.put("restaurantId", userDetails.getRestaurantId().toString());
        extraClaims.put("role", userDetails.getAuthorities().stream().findFirst().map(Object::toString).orElse(""));

        return buildToken(extraClaims, userDetails.getUsername(), jwtExpiration);
    }

    public String generateAdminToken(String adminUsername) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", SUPER_ADMIN_ROLE);

        return buildToken(extraClaims, adminUsername, adminJwtExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, String userEmail) {
        try {
            final String email = extractEmail(token);
            return (email.equalsIgnoreCase(userEmail)) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAdminTokenValid(String token, String expectedAdminUsername) {
        try {
            final String role = extractClaim(token, claims -> claims.get("role", String.class));
            final String subject = extractEmail(token);
            return SUPER_ADMIN_ROLE.equals(role)
                    && subject.equalsIgnoreCase(expectedAdminUsername)
                    && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
