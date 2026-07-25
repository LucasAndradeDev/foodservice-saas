package com.example.restaurant_saas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiConfig {

    @Bean
    public RestClient geminiRestClient(@Value("${gemini.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // First external synchronous HTTP call in this codebase (a Gemini round trip
        // can legitimately take tens of seconds) - explicit timeouts, nothing to inherit.
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(45_000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
