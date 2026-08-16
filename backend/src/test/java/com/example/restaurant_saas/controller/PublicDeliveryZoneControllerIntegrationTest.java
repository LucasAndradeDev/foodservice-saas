package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryZoneRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

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

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
    }

    private String registerOwnerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
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

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee").param("neighborhood", "CENTRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.fee").value(8.00));
    }

    @Test
    void getFeeQuote_forUnservedNeighborhood_shouldReturnUnavailable() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        createZone(token, "Centro", "8.00");

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee").param("neighborhood", "Bairro Distante"))
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

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/delivery/fee").param("neighborhood", "Centro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
