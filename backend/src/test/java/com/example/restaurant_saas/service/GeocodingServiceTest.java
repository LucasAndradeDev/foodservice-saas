package com.example.restaurant_saas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Reproduces the real bug found 2026-09-06 (see geocodeStructured's javadoc): Nominatim only ever
 * treats {@code postalcode} as a ranking hint, so its top result for a common street name can sit
 * in a completely different neighborhood than the one requested, with a totally different
 * postcode. geocodeStructured must catch that instead of trusting result[0] blindly.
 */
class GeocodingServiceTest {

    private MockRestServiceServer server;
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.test");
        server = MockRestServiceServer.bindTo(builder).build();
        geocodingService = new GeocodingService(builder.build());
    }

    @Test
    void discardsTheTopResultWhenItsPostcodeIsInADifferentArea() {
        server.expect(requestTo(containsString("/search")))
                .andExpect(queryParam("limit", "5"))
                .andExpect(queryParam("postalcode", "60853-505"))
                .andRespond(withSuccess("""
                        [
                          {"lat": "-3.7248068", "lon": "-38.6000306", "address": {"postcode": "60351-720"}},
                          {"lat": "-3.8263962", "lon": "-38.5139230", "address": {"postcode": "60863-435"}}
                        ]""", MediaType.APPLICATION_JSON));

        Optional<GeocodingService.GeoPoint> result =
                geocodingService.geocodeStructured("R. Um", "200", "Fortaleza", "60853-505");

        assertThat(result).contains(new GeocodingService.GeoPoint(-3.8263962, -38.5139230));
    }

    @Test
    void returnsEmptyWhenNoCandidateMatchesTheRequestedPostcodeArea() {
        server.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("""
                        [{"lat": "-3.7248068", "lon": "-38.6000306", "address": {"postcode": "60351-720"}}]""",
                        MediaType.APPLICATION_JSON));

        Optional<GeocodingService.GeoPoint> result =
                geocodingService.geocodeStructured("R. Um", "200", "Fortaleza", "60853-505");

        assertThat(result).isEmpty();
    }

    @Test
    void usesTheTopResultAsIsWhenNoZipCodeWasProvided() {
        server.expect(requestTo(containsString("/search")))
                .andExpect(queryParam("limit", "1"))
                .andRespond(withSuccess("""
                        [{"lat": "-3.7248068", "lon": "-38.6000306"}]""", MediaType.APPLICATION_JSON));

        Optional<GeocodingService.GeoPoint> result =
                geocodingService.geocodeStructured("R. Um", "200", "Fortaleza", null);

        assertThat(result).contains(new GeocodingService.GeoPoint(-3.7248068, -38.6000306));
    }
}
