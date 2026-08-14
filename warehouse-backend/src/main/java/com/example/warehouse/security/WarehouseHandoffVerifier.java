package com.example.warehouse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

/**
 * Verifies the short-lived SSO handoff token minted by Morá's WarehouseHandoffTokenService.
 * Stateless - both sides hold the same WAREHOUSE_SSO_SECRET, so no round-trip call back to
 * Morá is needed to validate it. See docs/ARMAZEM_MORA.md.
 */
@Service
public class WarehouseHandoffVerifier {

    private static final String EXPECTED_PURPOSE = "warehouse-sso";

    @Value("${warehouse.sso-secret}")
    private String secretKey;

    public HandoffClaims verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get("purpose", String.class);
        if (!EXPECTED_PURPOSE.equals(purpose)) {
            throw new JwtException("Unexpected token purpose: " + purpose);
        }

        UUID restaurantId = UUID.fromString(claims.get("restaurantId", String.class));
        String restaurantName = claims.get("restaurantName", String.class);
        String apiKey = claims.get("apiKey", String.class);

        return new HandoffClaims(restaurantId, restaurantName, apiKey);
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
