package com.example.restaurant_saas.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Real road-network distance (and duration, for the customer-facing ETA - docs/DELIVERY.md live
// courier tracking follow-up) for the distance-based delivery fee, used by DeliveryFeeResolver in
// place of the straight-line Haversine estimate it falls back to when every provider here is
// unavailable (Haversine has no notion of duration at all, so ETA is simply unavailable in that
// case - see DeliveryService#refreshEtaIfStale). Three free-tier providers tried in order - ORS's
// daily quota first, then Mapbox's monthly quota, then OSRM's public demo server (no key, no
// quota, but no uptime guarantee either) as the always-available last resort - same idea as
// GeminiService's model fallback chain, just across different providers instead of model names.
// A blank api-key means that provider isn't configured yet and is skipped without a wasted network
// call. Every failure mode (missing key, quota exceeded, timeout, no route found) falls through to
// the next provider rather than throwing - like GeocodingService, this is best-effort and must
// never block a fee quote/order.
@Service
public class RouteDistanceService {

    private static final Logger log = LoggerFactory.getLogger(RouteDistanceService.class);

    private final RestClient orsRestClient;
    private final RestClient mapboxRestClient;
    private final RestClient osrmRestClient;

    @Value("${routing.ors.api-key:}")
    private String orsApiKey;

    @Value("${routing.mapbox.api-key:}")
    private String mapboxApiKey;

    public RouteDistanceService(
            @Qualifier("orsRestClient") RestClient orsRestClient,
            @Qualifier("mapboxRestClient") RestClient mapboxRestClient,
            @Qualifier("osrmRestClient") RestClient osrmRestClient
    ) {
        this.orsRestClient = orsRestClient;
        this.mapboxRestClient = mapboxRestClient;
        this.osrmRestClient = osrmRestClient;
    }

    public record RouteResult(double distanceKm, double durationMinutes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrsResponse(List<OrsRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrsRoute(OrsSummary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrsSummary(double distance, double duration) {
    }

    // Mapbox and OSRM both put distance/duration directly on each route, no nested "summary" -
    // same response shape, so both share this one record. OSRM also has a top-level "code" that
    // must be "Ok" (e.g. "NoRoute" means the two points aren't connected in its road graph).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimpleRouteResponse(String code, List<SimpleRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SimpleRoute(double distance, double duration) {
    }

    public Optional<RouteResult> route(double fromLat, double fromLon, double toLat, double toLon) {
        if (!orsApiKey.isBlank()) {
            Optional<RouteResult> result = tryOrs(fromLat, fromLon, toLat, toLon);
            if (result.isPresent()) {
                return result;
            }
        }
        if (!mapboxApiKey.isBlank()) {
            Optional<RouteResult> result = tryMapbox(fromLat, fromLon, toLat, toLon);
            if (result.isPresent()) {
                return result;
            }
        }
        return tryOsrm(fromLat, fromLon, toLat, toLon);
    }

    private Optional<RouteResult> tryOrs(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            OrsResponse response = orsRestClient.post()
                    .uri("/v2/directions/driving-car")
                    .header("Authorization", orsApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("coordinates", List.of(List.of(fromLon, fromLat), List.of(toLon, toLat))))
                    .retrieve()
                    .body(OrsResponse.class);
            if (response == null || response.routes() == null || response.routes().isEmpty() || response.routes().get(0).summary() == null) {
                return Optional.empty();
            }
            OrsSummary summary = response.routes().get(0).summary();
            return Optional.of(toRouteResult(summary.distance(), summary.duration()));
        } catch (RestClientResponseException e) {
            log.warn("OpenRouteService failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("OpenRouteService timed out or failed to connect", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OpenRouteService returned an unparseable response", e);
            return Optional.empty();
        }
    }

    private Optional<RouteResult> tryMapbox(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            SimpleRouteResponse response = mapboxRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/directions/v5/mapbox/driving/{coordinates}")
                            .queryParam("access_token", mapboxApiKey)
                            .queryParam("overview", "false")
                            .build(coordinatesPath(fromLat, fromLon, toLat, toLon)))
                    .retrieve()
                    .body(SimpleRouteResponse.class);
            return firstRouteResult(response);
        } catch (RestClientResponseException e) {
            log.warn("Mapbox Directions failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("Mapbox Directions timed out or failed to connect", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Mapbox Directions returned an unparseable response", e);
            return Optional.empty();
        }
    }

    private Optional<RouteResult> tryOsrm(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            SimpleRouteResponse response = osrmRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coordinates}")
                            .queryParam("overview", "false")
                            .build(coordinatesPath(fromLat, fromLon, toLat, toLon)))
                    .retrieve()
                    .body(SimpleRouteResponse.class);
            if (response != null && !"Ok".equals(response.code())) {
                return Optional.empty();
            }
            return firstRouteResult(response);
        } catch (RestClientResponseException e) {
            log.warn("OSRM failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("OSRM timed out or failed to connect", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OSRM returned an unparseable response", e);
            return Optional.empty();
        }
    }

    private Optional<RouteResult> firstRouteResult(SimpleRouteResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }
        SimpleRoute route = response.routes().get(0);
        return Optional.of(toRouteResult(route.distance(), route.duration()));
    }

    // "{lon},{lat};{lon},{lat}" - Locale.ROOT so a pt-BR JVM default never formats the decimal
    // point as a comma, which would silently corrupt the URL.
    private String coordinatesPath(double fromLat, double fromLon, double toLat, double toLon) {
        return String.format(Locale.ROOT, "%f,%f;%f,%f", fromLon, fromLat, toLon, toLat);
    }

    private RouteResult toRouteResult(double distanceMeters, double durationSeconds) {
        return new RouteResult(distanceMeters / 1000.0, durationSeconds / 60.0);
    }
}
