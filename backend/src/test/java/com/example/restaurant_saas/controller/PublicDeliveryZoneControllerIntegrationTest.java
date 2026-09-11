package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryZoneRequest;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.service.GeocodingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicDeliveryZoneControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @MockBean
    private GeocodingService geocodingService;

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
    }

    // A new signup is unapproved by default (AuthService#registerRestaurant) and can't log in -
    // approve it directly then log in to get a working token, since registration itself no
    // longer hands one out.
    private String registerOwnerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String restaurantId = JsonPath.read(result.getResponse().getContentAsString(), "$.restaurant.id");
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(restaurantId)).orElseThrow();
        restaurant.setApproved(true);
        restaurantRepository.save(restaurant);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword(registerRequest.getOwnerPassword());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    // Bypasses RestaurantService's real geocoding (which would hit the network for the
    // restaurant's own address) - sets the distance-mode fields directly, same as if a previous
    // settings save had already geocoded successfully.
    private void configureDistanceMode(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug).orElseThrow();
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        restaurantRepository.save(restaurant);
    }

    private void createZone(String token, String neighborhood, String fee) throws Exception {
        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood(neighborhood);
        request.setFee(new BigDecimal(fee));
        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void getFeeQuote_forServedNeighborhood_caseInsensitive_shouldReturnFee() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        createZone(token, "Centro", "8.00");

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee")
                        .param("street", "Rua Um").param("number", "100").param("neighborhood", "CENTRO").param("city", "Cidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.fee").value(8.00))
                .andExpect(jsonPath("$.method").value("ZONE"));
    }

    @Test
    void getFeeQuote_forUnservedNeighborhood_shouldReturnUnavailable() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        createZone(token, "Centro", "8.00");

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee")
                        .param("street", "Rua Um").param("number", "100").param("neighborhood", "Bairro Distante").param("city", "Cidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.fee").doesNotExist());
    }

    @Test
    void getFeeQuote_forInactiveZone_shouldReturnUnavailable() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood("Centro");
        request.setFee(new BigDecimal("8.00"));
        MvcResult created = mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String zoneId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        UpdateDeliveryZoneRequest updateRequest = new UpdateDeliveryZoneRequest();
        updateRequest.setNeighborhood("Centro");
        updateRequest.setFee(new BigDecimal("8.00"));
        updateRequest.setActive(false);
        mockMvc.perform(put("/api/v1/delivery-zones/" + zoneId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee")
                        .param("street", "Rua Um").param("number", "100").param("neighborhood", "Centro").param("city", "Cidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void getFeeQuote_withDistanceModeConfigured_andGeocodeSucceeds_shouldPriceByDistance() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        configureDistanceMode(slug);
        createZone(token, "Centro", "8.00");
        // ~1.57km away from the restaurant's configured position by Haversine.
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5637, -46.6528)));

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee")
                        .param("street", "Rua Dois").param("number", "50").param("neighborhood", "Centro").param("city", "Cidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.method").value("DISTANCE"))
                .andExpect(jsonPath("$.distanceKm").exists())
                // base 5.00 + 2.00/km, distance > 0 so fee must exceed the flat zone fee (8.00).
                .andExpect(jsonPath("$.fee").value(org.hamcrest.Matchers.greaterThan(5.0)));
    }

    @Test
    void getFeeQuote_withDistanceModeConfigured_andGeocodeFails_shouldFallBackToZone() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        configureDistanceMode(slug);
        createZone(token, "Centro", "8.00");
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee")
                        .param("street", "Rua Inexistente 9999").param("number", "0").param("neighborhood", "CENTRO").param("city", "Cidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.method").value("ZONE"))
                .andExpect(jsonPath("$.fee").value(8.00))
                .andExpect(jsonPath("$.distanceKm").doesNotExist());
    }
}
