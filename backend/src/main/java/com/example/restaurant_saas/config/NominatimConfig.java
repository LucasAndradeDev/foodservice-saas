package com.example.restaurant_saas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NominatimConfig {

    @Bean
    public RestClient nominatimRestClient(@Value("${nominatim.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // A geocoding lookup, not an AI generation call - short timeouts, unlike GeminiConfig's.
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                // Nominatim's usage policy requires identifying the application and a contact in
                // User-Agent (no API key exists to identify us instead) - same mailbox already
                // used as the Brevo sender, see docs/DELIVERY.md.
                .defaultHeader("User-Agent", "MoraSaaS-Delivery/1.0 (contact: jardellucas078@gmail.com)")
                .requestFactory(requestFactory)
                .build();
    }
}
