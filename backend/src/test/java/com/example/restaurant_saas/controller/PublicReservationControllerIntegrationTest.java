package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.PublicCreateReservationRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
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

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicReservationControllerIntegrationTest {

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

    private void createTable(String token, int capacity) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setCapacity(capacity);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private MvcResult createPublicReservation(String slug, String phone, OffsetDateTime time) throws Exception {
        PublicCreateReservationRequest request = new PublicCreateReservationRequest();
        request.setCustomerName("Jane Doe");
        request.setCustomerPhone(phone);
        request.setPartySize(2);
        request.setReservationTime(time);
        return mockMvc.perform(post("/api/v1/public/menu/" + slug + "/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Test
    void createReservation_public_returnsAccessTokenAndAssignsTable() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTable(ownerToken, 4);
        String slug = getSlug(ownerToken);

        MvcResult result = createPublicReservation(slug, "11988887777", OffsetDateTime.now().plusHours(2));

        assertEquals(201, result.getResponse().getStatus());
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertNotNull(accessToken);
    }

    @Test
    void getByToken_returnsReservation() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTable(ownerToken, 4);
        String slug = getSlug(ownerToken);

        MvcResult createResult = createPublicReservation(slug, "11977776666", OffsetDateTime.now().plusHours(2));
        String accessToken = JsonPath.read(createResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/public/reservations/" + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void getByToken_invalidToken_returnsGenericNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/reservations/not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reservation not found."));
    }

    @Test
    void cancelByToken_cancelsThenRejectsSecondCancel() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTable(ownerToken, 4);
        String slug = getSlug(ownerToken);

        MvcResult createResult = createPublicReservation(slug, "11966665555", OffsetDateTime.now().plusHours(2));
        String accessToken = JsonPath.read(createResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(delete("/api/v1/public/reservations/" + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/api/v1/public/reservations/" + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReservation_public_rateLimitedAfterFiveAttempts() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTable(ownerToken, 4);
        String slug = getSlug(ownerToken);
        String phone = "11955554444";

        // Spaced two hours apart so none of these five collide with each other (default block window
        // is 30 minutes either side of the reservation time) -- only the rate limiter should stop the
        // sixth attempt, not a scheduling conflict.
        for (int i = 1; i <= 5; i++) {
            MvcResult result = createPublicReservation(slug, phone, OffsetDateTime.now().plusHours(2L * i));
            assertEquals(201, result.getResponse().getStatus());
        }

        MvcResult sixth = createPublicReservation(slug, phone, OffsetDateTime.now().plusHours(20));
        assertEquals(429, sixth.getResponse().getStatus());
    }
}
