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

    // How many digits of a Brazilian CEP identify the general sector (a cluster of a few
    // neighborhoods, not a single one) - deliberately coarse: real Fortaleza data shows adjacent
    // neighborhoods (e.g. Barroso/Cajazeiras) sharing the first 4 digits already, and the last
    // digits routinely differ even for two addresses on the same street depending on which side/
    // range they fall in. The goal here is only to catch a gross wrong-region mismatch (see
    // geocodeStructured's javadoc), not to demand precision Nominatim/CEP data doesn't reliably
    // give at finer granularity.
    private static final int ZIP_AREA_PREFIX_LENGTH = 3;

    private final RestClient nominatimRestClient;

    public record GeoPoint(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon, NominatimAddress address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimAddress(String postcode) {
    }

    public Optional<GeoPoint> geocode(String freeFormAddress) {
        NominatimResult[] results = search(uriBuilder -> uriBuilder.queryParam("q", freeFormAddress), 1);
        return results.length == 0 ? Optional.empty() : toGeoPoint(results[0]);
    }

    /**
     * Structured search (street/city/postalcode passed as their own Nominatim params) resolves
     * more reliably than free text, per Nominatim's own guidance - used whenever the address is
     * already split into fields (restaurant's own address, task 26.5 follow-up; customer delivery
     * address, already collected this way since task 25). Returns empty without calling Nominatim
     * when neither street nor city is present - a structured query needs at least one of those.
     *
     * <p><b>Cross-checked against the requested postal code, not just the top hit</b> (bug found
     * 2026-09-06 testing delivery: a restaurant on "Rua 1"/"R. Um" in Barroso geocoded to a
     * same-named "Rua 1" in Quintino Cunha instead, a different Fortaleza neighborhood entirely,
     * with a completely different postcode). Nominatim only ever treats {@code postalcode} as a
     * ranking hint, never a hard filter, so a common street name repeated across several
     * neighborhoods can outrank the correct match even with the right postcode passed in. When a
     * zip code is available, this asks for a few candidates and picks the first whose own
     * {@code address.postcode} actually starts with the same {@value #ZIP_AREA_PREFIX_LENGTH}-digit
     * area prefix - not requiring an exact match (the last digits are per-building/per-street and
     * routinely differ), just the same general area. If none of the candidates agree, this trusts
     * none of them over a wrong-neighborhood coordinate and returns empty - same as any other
     * unresolvable address, letting the caller fall back to {@code DeliveryZone}.
     */
    public Optional<GeoPoint> geocodeStructured(String street, String number, String city, String zipCode) {
        boolean hasStreet = street != null && !street.isBlank();
        boolean hasCity = city != null && !city.isBlank();
        if (!hasStreet && !hasCity) {
            return Optional.empty();
        }

        String expectedAreaPrefix = zipAreaPrefix(zipCode);
        boolean hasZip = expectedAreaPrefix != null;

        NominatimResult[] results = search(uriBuilder -> {
            if (hasStreet) {
                String houseAndStreet = number != null && !number.isBlank() ? number + " " + street : street;
                uriBuilder.queryParam("street", houseAndStreet);
            }
            if (hasCity) {
                uriBuilder.queryParam("city", city);
            }
            if (hasZip) {
                uriBuilder.queryParam("postalcode", zipCode);
            }
        }, hasZip ? 5 : 1);

        if (!hasZip) {
            return results.length == 0 ? Optional.empty() : toGeoPoint(results[0]);
        }

        for (NominatimResult result : results) {
            String actualAreaPrefix = result.address() != null ? zipAreaPrefix(result.address().postcode()) : null;
            if (!expectedAreaPrefix.equals(actualAreaPrefix)) {
                continue;
            }
            // Keeps checking the rest of the batch instead of giving up outright on a rare
            // malformed lat/lon (found in review 2026-09-07) - a later candidate already fetched
            // in this same batch might still match the area and parse fine.
            Optional<GeoPoint> point = toGeoPoint(result);
            if (point.isPresent()) {
                return point;
            }
        }
        log.warn("Nominatim returned no candidate matching postcode area {} for street '{}', city '{}' - discarding all {} result(s)",
                expectedAreaPrefix, street, city, results.length);
        return Optional.empty();
    }

    // Digits-only prefix (Brazilian CEPs are formatted "12345-678", but Nominatim doesn't always
    // echo the hyphen back consistently) - null when there aren't enough digits to compare.
    private String zipAreaPrefix(String zipCode) {
        if (zipCode == null) {
            return null;
        }
        String digits = zipCode.replaceAll("\\D", "");
        return digits.length() >= ZIP_AREA_PREFIX_LENGTH ? digits.substring(0, ZIP_AREA_PREFIX_LENGTH) : null;
    }

    private Optional<GeoPoint> toGeoPoint(NominatimResult result) {
        try {
            return Optional.of(new GeoPoint(Double.parseDouble(result.lat()), Double.parseDouble(result.lon())));
        } catch (NumberFormatException e) {
            log.warn("Nominatim returned an unparseable coordinate", e);
            return Optional.empty();
        }
    }

    private NominatimResult[] search(Consumer<UriBuilder> queryParams, int limit) {
        try {
            NominatimResult[] results = nominatimRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("addressdetails", "1")
                                .queryParam("limit", limit)
                                .queryParam("countrycodes", "br");
                        queryParams.accept(uriBuilder);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(NominatimResult[].class);

            return results != null ? results : new NominatimResult[0];
        } catch (RestClientResponseException e) {
            log.warn("Nominatim geocoding failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new NominatimResult[0];
        } catch (ResourceAccessException e) {
            log.warn("Nominatim geocoding timed out or failed to connect", e);
            return new NominatimResult[0];
        }
    }
}
