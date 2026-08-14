package com.example.warehouse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Armazém Morá's own session token, minted once a handoff token has been verified (see
 * WarehouseSsoService). Signed with its own secret - never shared with Morá, unlike
 * WAREHOUSE_SSO_SECRET - so a leak here can't be used to forge anything on the Morá side.
 */
@Service
public class WarehouseJwtService {

    @Value("${warehouse.jwt-secret}")
    private String secretKey;

    @Value("${warehouse.jwt-expiration-ms}")
    private long expirationMs;

    public String generateToken(UUID restaurantLinkId) {
        return Jwts.builder()
                .subject(restaurantLinkId.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    public UUID extractRestaurantLinkId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
