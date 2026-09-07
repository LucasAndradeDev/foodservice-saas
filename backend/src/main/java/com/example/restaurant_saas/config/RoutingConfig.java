package com.example.restaurant_saas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Three routing providers tried in order by RouteDistanceService (ORS -> Mapbox -> OSRM), each
// with its own free tier/quota - see docs/DELIVERY.md "v3: rota real". Same short-timeout
// reasoning as NominatimConfig: this blocks a fee quote/order, not a background job. Bean names
// (= method names) are how RouteDistanceService's constructor picks each one apart via @Qualifier
// - Spring can't disambiguate three same-typed RestClient beans any other way.
@Configuration
public class RoutingConfig {

    @Bean
    public RestClient orsRestClient(@Value("${routing.ors.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(shortTimeoutRequestFactory()).build();
    }

    @Bean
    public RestClient mapboxRestClient(@Value("${routing.mapbox.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(shortTimeoutRequestFactory()).build();
    }

    @Bean
    public RestClient osrmRestClient(@Value("${routing.osrm.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(shortTimeoutRequestFactory()).build();
    }

    private SimpleClientHttpRequestFactory shortTimeoutRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        return requestFactory;
    }
}
