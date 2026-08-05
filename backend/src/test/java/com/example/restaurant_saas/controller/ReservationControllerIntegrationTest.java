package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateReservationRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setPhone("11999999999");
        registerRequest.setAddress("Main St, 100");
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

    private User createUserDirectly(UserRole role) {
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User user = User.builder()
                .restaurant(owner.getRestaurant())
                .name(role.name())
                .email(role.name().toLowerCase() + "+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    private String createTableWithCapacity(String token, int capacity) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setCapacity(capacity);
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult createReservation(String token, int partySize, OffsetDateTime time, List<String> tableIds) throws Exception {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setCustomerName("Jane Doe");
        request.setCustomerPhone("11988887777");
        request.setPartySize(partySize);
        request.setReservationTime(time);
        if (tableIds != null) {
            request.setTableIds(tableIds.stream().map(UUID::fromString).toList());
        }
        return mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Test
    void createReservation_autoAssignsSmallestFittingTable() throws Exception {
        String token = registerOwnerAndGetToken();
        createTableWithCapacity(token, 2);
        String bigTableId = createTableWithCapacity(token, 4);

        // Fixed offset from "now" rather than a wall-clock time: plusHours(2) alone would cross into the
        // next calendar day whenever the suite runs late at night, and the list query below needs the
        // reservation's own date, not today's.
        OffsetDateTime reservationTime = OffsetDateTime.now().plusHours(2);
        MvcResult result = createReservation(token, 3, reservationTime, null);
        String body = result.getResponse().getContentAsString();
        String reservationId = JsonPath.read(body, "$.id");
        assertEquals(201, result.getResponse().getStatus());
        List<String> tableIds = JsonPath.read(body, "$.tables[*].id");
        assertEquals(1, tableIds.size());
        assertEquals(bigTableId, tableIds.get(0));

        MvcResult listResult = mockMvc.perform(get("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .param("date", reservationTime.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andReturn();
        List<String> listedIds = JsonPath.read(listResult.getResponse().getContentAsString(), "$[*].id");
        assertEquals(List.of(reservationId), listedIds);
    }

    @Test
    void createReservation_conflictingTime_returnsForbidden() throws Exception {
        String token = registerOwnerAndGetToken();
        createTableWithCapacity(token, 4);

        OffsetDateTime time = OffsetDateTime.now().plusHours(2);
        MvcResult first = createReservation(token, 4, time, null);
        assertEquals(201, first.getResponse().getStatus());

        // Same (only) table, a few minutes later -- well within the default 30-min block window on
        // either side, so it collides with the first reservation even though party size 2 would
        // otherwise easily fit.
        MvcResult second = createReservation(token, 2, time.plusMinutes(5), null);
        assertEquals(403, second.getResponse().getStatus());
    }

    @Test
    void createReservation_autoAssignSkipsCurrentlyOccupiedTable() throws Exception {
        String token = registerOwnerAndGetToken();
        String tableId = createTableWithCapacity(token, 4);

        OpenTabRequest openTabRequest = new OpenTabRequest();
        openTabRequest.setTableIds(List.of(UUID.fromString(tableId)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openTabRequest)))
                .andExpect(status().isCreated());

        // The only table fits the party and has no conflicting reservation, but it's occupied by a
        // walk-in right now -- auto-assign must not hand it out, even for a reservation far in the
        // future, since it can never be sure the table will actually be free by then.
        MvcResult result = createReservation(token, 2, OffsetDateTime.now().plusHours(2), null);
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void createReservation_explicitTableInsufficientCapacity_returnsBadRequest() throws Exception {
        String token = registerOwnerAndGetToken();
        String tableId = createTableWithCapacity(token, 2);

        MvcResult result = createReservation(token, 5, OffsetDateTime.now().plusHours(2), List.of(tableId));
        assertEquals(400, result.getResponse().getStatus());
    }

    @Test
    void createReservation_blocksOpeningTabOnThatTableWhileWindowActive() throws Exception {
        String token = registerOwnerAndGetToken();
        String tableId = createTableWithCapacity(token, 4);

        // Default block window is 30 min before / 30 min after; a reservation 10 minutes from now is
        // already inside that window the instant it's created.
        createReservation(token, 2, OffsetDateTime.now().plusMinutes(10), List.of(tableId));

        OpenTabRequest openTabRequest = new OpenTabRequest();
        openTabRequest.setTableIds(List.of(UUID.fromString(tableId)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openTabRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkIn_opensTabAndMarksReservationSeated() throws Exception {
        String token = registerOwnerAndGetToken();
        String tableId = createTableWithCapacity(token, 4);

        MvcResult createResult = createReservation(token, 2, OffsetDateTime.now().plusMinutes(10), List.of(tableId));
        String reservationId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        MvcResult checkInResult = mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String status = JsonPath.read(checkInResult.getResponse().getContentAsString(), "$.status");
        String tabId = JsonPath.read(checkInResult.getResponse().getContentAsString(), "$.tabId");
        assertEquals("SEATED", status);
        assertNotNull(tabId);

        mockMvc.perform(get("/api/v1/tabs/" + tabId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void cancelReservation_freesTableImmediately() throws Exception {
        String token = registerOwnerAndGetToken();
        String tableId = createTableWithCapacity(token, 4);

        MvcResult createResult = createReservation(token, 2, OffsetDateTime.now().plusMinutes(10), List.of(tableId));
        String reservationId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        OpenTabRequest openTabRequest = new OpenTabRequest();
        openTabRequest.setTableIds(List.of(UUID.fromString(tableId)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openTabRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    void createReservation_kitchenRoleForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTableWithCapacity(ownerToken, 4);
        User kitchen = createUserDirectly(UserRole.KITCHEN);

        MvcResult result = createReservation(tokenFor(kitchen), 2, OffsetDateTime.now().plusHours(2), null);
        assertEquals(403, result.getResponse().getStatus());
    }
}
