package com.example.restaurant_saas.dto.request;

import lombok.Data;

/**
 * Both fields are optional here on purpose: {@link com.example.restaurant_saas.service.CardChargeService#saveIntegration}
 * treats a blank field as "leave this one as it already is" - only required together the first
 * time a restaurant connects (nothing to fall back to yet). Lets an owner rotate just the access
 * token, or just the webhook secret, without being forced to re-paste the other one too.
 */
@Data
public class SaveCardIntegrationRequest {

    private String accessToken;

    private String webhookSecret;
}
