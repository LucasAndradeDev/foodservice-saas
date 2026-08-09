package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Reservation;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.PublicCreateReservationRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.support.TenantTestSupport;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@link ReservationRepository#findByAccessTokenBypassingRls} - the native query backed by
 * the {@code reservation_by_access_token} SECURITY DEFINER function from V49, used by
 * {@code ReservationService}'s token-based flows - maps its result set back to {@link Reservation}
 * correctly. Also demonstrates the contrast the bypass exists for: with RLS live (V50/V51), the
 * ordinary {@code findByAccessToken} only sees the row once the tenant is set, while the bypass
 * function finds it either way.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationRepositoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
    }

    @Test
    void findByAccessTokenBypassingRls_mapsToTheSameReservationAsTheFilteredLookup() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTable(ownerToken);
        String slug = getSlug(ownerToken);
        String accessToken = createPublicReservation(slug, "11988887777");
        UUID restaurantId = restaurantRepository.findBySlug(slug).orElseThrow().getId();

        // The ordinary filtered lookup only sees the row once the tenant is known - exactly the
        // gap the bypass function exists to close (it works with no tenant set at all, below).
        Optional<Reservation> viaFilteredLookup = TenantTestSupport.withTenant(restaurantId,
                () -> reservationRepository.findByAccessToken(accessToken));
        Optional<Reservation> viaBypassFunction = reservationRepository.findByAccessTokenBypassingRls(accessToken);

        assertThat(viaFilteredLookup).isPresent();
        assertThat(viaBypassFunction).isPresent();
        assertThat(viaBypassFunction.get().getId()).isEqualTo(viaFilteredLookup.get().getId());
        assertThat(viaBypassFunction.get().getCustomerName()).isEqualTo("Jane Doe");
        assertThat(viaBypassFunction.get().getCustomerPhone()).isEqualTo("11988887777");
        assertThat(viaBypassFunction.get().getAccessToken()).isEqualTo(accessToken);
    }

    @Test
    void findByAccessTokenBypassingRls_withUnknownToken_returnsEmpty() {
        assertThat(reservationRepository.findByAccessTokenBypassingRls("no-such-token")).isEmpty();
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

    private void createTable(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setCapacity(4);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String createPublicReservation(String slug, String phone) throws Exception {
        PublicCreateReservationRequest request = new PublicCreateReservationRequest();
        request.setCustomerName("Jane Doe");
        request.setCustomerPhone(phone);
        request.setPartySize(2);
        request.setReservationTime(OffsetDateTime.now().plusHours(2));
        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
