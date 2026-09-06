package com.example.restaurant_saas.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.util.Optional;
import java.util.function.Consumer;

// Wraps Nominatim (OpenStreetMap's free geocoding service, see docs/DELIVERY.md "v2: geo real").
// Every failure mode (no result, bad address, timeout, Nominatim down) is swallowed into
// Optional.empty() rather than thrown - geocoding is best-effort for DeliveryFeeResolver, which
// always has the DeliveryZone table to fall back to. An order/quote must never fail just because
// this free third-party service had a bad moment.
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final RestClient nominatimRestClient;

    public record GeoPoint(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon) {
    }

    public Optional<GeoPoint> geocode(String freeFormAddress) {
        return search(uriBuilder -> uriBuilder.queryParam("q", freeFormAddress));
    }

    // Structured search (street/city/postalcode passed as their own Nominatim params) resolves
    // more reliably than free text, per Nominatim's own guidance - used whenever the address is
    // already split into fields (restaurant's own address, task 26.5 follow-up; customer delivery
    // address, already collected this way since task 25). Returns empty without calling Nominatim
    // when neither street nor city is present - a structured query needs at least one of those.
    public Optional<GeoPoint> geocodeStructured(String street, String number, String city, String zipCode) {
        boolean hasStreet = street != null && !street.isBlank();
        boolean hasCity = city != null && !city.isBlank();
        if (!hasStreet && !hasCity) {
            return Optional.empty();
        }
        return search(uriBuilder -> {
            if (hasStreet) {
                String houseAndStreet = number != null && !number.isBlank() ? number + " " + street : street;
                uriBuilder.queryParam("street", houseAndStreet);
            }
            if (hasCity) {
                uriBuilder.queryParam("city", city);
            }
            if (zipCode != null && !zipCode.isBlank()) {
                uriBuilder.queryParam("postalcode", zipCode);
            }
        });
    }

    private Optional<GeoPoint> search(Consumer<UriBuilder> queryParams) {
        try {
            NominatimResult[] results = nominatimRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("limit", 1)
                                .queryParam("countrycodes", "br");
                        queryParams.accept(uriBuilder);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(NominatimResult[].class);

            if (results == null || results.length == 0) {
                return Optional.empty();
            }
            return Optional.of(new GeoPoint(Double.parseDouble(results[0].lat()), Double.parseDouble(results[0].lon())));
        } catch (RestClientResponseException e) {
            log.warn("Nominatim geocoding failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("Nominatim geocoding timed out or failed to connect", e);
            return Optional.empty();
        } catch (NumberFormatException e) {
            log.warn("Nominatim returned an unparseable coordinate", e);
            return Optional.empty();
        }
    }
}
