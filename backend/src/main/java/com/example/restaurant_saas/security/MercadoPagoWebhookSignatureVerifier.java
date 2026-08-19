package com.example.restaurant_saas.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Verifies Mercado Pago's {@code x-signature} webhook header: HMAC-SHA256 (hex) over a manifest
 * string, checked against a secret unique to each restaurant's own Mercado Pago application
 * (confirmed against developers.mercadopago.com during design) - unlike Woovi's fixed RSA public
 * key shared by every account, this secret must be supplied per call, decrypted from
 * {@code CardIntegration.webhookSecretEncrypted}.
 *
 * <p>The header itself is a comma-separated {@code key=value} list, e.g.
 * {@code ts=1742505638683,v1=<hex digest>}. The manifest signed is
 * {@code id:{data.id};request-id:{x-request-id};ts:{ts};} - {@code id:}/{@code request-id:}
 * segments are included only when the corresponding value is present, {@code ts:} is always
 * present. IMPORTANT: this format was captured from Mercado Pago's public docs during design and
 * MUST be double-checked against developers.mercadopago.com before the first real webhook is
 * processed in production - same caution the Woovi integration's RSA key needed.
 *
 * <p>Confirmed against real sandbox traffic (docs/CARD_PAYMENT.md, "Pegadinha do teste manual no
 * sandbox"): this formula matches Mercado Pago's own docs exactly and validates correctly for
 * simulated webhooks - a real sandbox purchase (live_mode: true) arrives signed with a different
 * key than the one shown in the dashboard, a confirmed Mercado Pago sandbox quirk, not a bug here.
 * Rejecting those is correct; see {@link com.example.restaurant_saas.service.CardChargeService
 * #verifyPendingChargeByExternalReference} for the second, non-webhook confirmation path this
 * relies on instead.
 */
@Component
public class MercadoPagoWebhookSignatureVerifier {

    public boolean isValid(String signatureHeader, String requestIdHeader, String dataId, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }

        Map<String, String> parts = parseHeader(signatureHeader);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }

        String manifest = buildManifest(dataId, requestIdHeader, ts);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(digest);

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8)
            );
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private String buildManifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId).append(";");
        }
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");
        return manifest.toString();
    }

    private Map<String, String> parseHeader(String signatureHeader) {
        Map<String, String> parts = new java.util.HashMap<>();
        for (String segment : signatureHeader.split(",")) {
            String[] pair = segment.split("=", 2);
            if (pair.length == 2) {
                parts.put(pair[0].trim(), pair[1].trim());
            }
        }
        return parts;
    }
}
