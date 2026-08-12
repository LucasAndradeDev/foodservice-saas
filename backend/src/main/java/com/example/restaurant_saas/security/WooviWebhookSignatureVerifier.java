package com.example.restaurant_saas.security;

import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies the {@code x-webhook-signature} header Woovi sends on every webhook call: RSA/SHA-256
 * over the raw request body, checked against Woovi's public key - the same key for every Woovi
 * account, published at
 * https://developers.woovi.com/docs/webhook/seguranca/webhook-signature-validation. Unlike an
 * HMAC scheme, there's no per-restaurant secret involved: this key is public by design (it can
 * only verify, never forge, a signature), so it's safe to hardcode rather than configure.
 *
 * IMPORTANT: this value was captured from Woovi's public docs during the design of this feature
 * and MUST be double-checked against developers.woovi.com before the first real webhook is
 * processed in production - a stale or mistyped key here would silently reject every legitimate
 * webhook.
 */
@Component
public class WooviWebhookSignatureVerifier {

    private static final String WOOVI_PUBLIC_KEY_BASE64 =
            "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlHZk1BMEdDU3FHU0liM0RRRUJBUVVBQTRHTkFEQ0JpUUtCZ1FDLytOdElranpldnZxRCtJM01NdjNiTFhEdApwdnhCalk0QnNSclNkY2EzcnRBd01jUllZdnhTbmQ3amFnVkxwY3RNaU94UU84aWVVQ0tMU1dIcHNNQWpPL3paCldNS2Jxb0c4TU5waS91M2ZwNnp6MG1jSENPU3FZc1BVVUcxOWJ1VzhiaXM1WloySVpnQk9iV1NwVHZKMGNuajYKSEtCQUE4MkpsbitsR3dTMU13SURBUUFCCi0tLS0tRU5EIFBVQkxJQyBLRVktLS0tLQo=";

    private final PublicKey publicKey;

    public WooviWebhookSignatureVerifier() {
        this.publicKey = loadPublicKey();
    }

    public boolean isValid(byte[] rawBody, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(rawBody);
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private PublicKey loadPublicKey() {
        try {
            String pem = new String(Base64.getDecoder().decode(WOOVI_PUBLIC_KEY_BASE64))
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load Woovi's webhook public key", e);
        }
    }
}
