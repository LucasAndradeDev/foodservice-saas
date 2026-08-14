package com.example.restaurant_saas.security;

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

/**
 * Mints the short-lived, single-purpose token used to SSO an owner/manager from the Morá
 * sidebar into Armazém Morá (a separate app/origin) without logging in twice - see
 * docs/ARMAZEM_MORA.md. Signed with a secret dedicated to this one purpose
 * ({@code warehouse.sso-secret} / WAREHOUSE_SSO_SECRET), shared with the Armazém Morá backend
 * but deliberately NOT the same as {@code api.jwt.secret}: a leak here only lets someone forge
 * a ~60s handoff, not a full Morá session.
 */
@Service
public class WarehouseHandoffTokenService {

    private static final long EXPIRATION_MS = 60_000;
    private static final String PURPOSE = "warehouse-sso";

    @Value("${warehouse.sso-secret}")
    private String secretKey;

    public String generateHandoffToken(UUID restaurantId, String restaurantName, String apiKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", PURPOSE);
        claims.put("restaurantId", restaurantId.toString());
        claims.put("restaurantName", restaurantName);
        claims.put("apiKey", apiKey);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSignInKey())
                .compact();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
