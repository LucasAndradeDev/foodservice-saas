package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateRestaurantRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RestaurantControllerIntegrationTest {

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

    private String registerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void getMyRestaurant_withValidToken_shouldReturnRestaurantData() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Burger House"));
    }

    @Test
    void registerRestaurant_shouldAutoGenerateSlugFromName() throws Exception {
        String uniqueName = "Slug Test Grill " + System.nanoTime();
        registerRequest.setRestaurantName(uniqueName);
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(uniqueName.toLowerCase().replace(" ", "-")));
    }

    @Test
    void registerRestaurant_withDuplicateName_shouldGetDisambiguatedSlug() throws Exception {
        String uniqueName = "Dup Test Grill " + System.nanoTime();
        registerRequest.setRestaurantName(uniqueName);
        registerAndGetToken();

        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName(uniqueName);
        registerRequest.setOwnerName("Owner 2");
        registerRequest.setOwnerEmail("owner2+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        String secondToken = registerAndGetToken();

        String expectedSlug = uniqueName.toLowerCase().replace(" ", "-");
        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(expectedSlug + "-2"));
    }

    @Test
    void updateMyRestaurant_withSlug_shouldUpdateSlug() throws Exception {
        String token = registerAndGetToken();
        String customSlug = "my-custom-slug-" + System.nanoTime();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setSlug(customSlug);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(customSlug));
    }

    @Test
    void updateMyRestaurant_withInvalidSlugFormat_shouldReturn400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\": \"Not A Valid Slug!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyRestaurant_withSlugAlreadyUsedByAnotherRestaurant_shouldReturn400() throws Exception {
        String firstToken = registerAndGetToken();
        MvcResult firstResult = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andReturn();
        String firstSlug = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.slug");

        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Pizza Place");
        registerRequest.setOwnerName("Owner 2");
        registerRequest.setOwnerEmail("owner2+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        String secondToken = registerAndGetToken();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setSlug(firstSlug);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyRestaurant_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMyRestaurant_asWaiter_shouldSucceed() throws Exception {
        registerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();

        User waiter = User.builder()
                .restaurant(owner.getRestaurant())
                .name("Waiter")
                .email("waiter+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.WAITER)
                .active(true)
                .build();
        waiter = userRepository.save(waiter);

        String waiterToken = jwtService.generateToken(new UserDetailsImpl(waiter));

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Burger House"));
    }

    @Test
    void updateMyRestaurant_asOwner_shouldUpdateSettings() throws Exception {
        String token = registerAndGetToken();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setTradeName("Burger House Downtown");
        updateRequest.setLogo("https://cdn.test.com/logo.png");
        updateRequest.setTableCount(10);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Burger House Downtown"))
                .andExpect(jsonPath("$.logo").value("https://cdn.test.com/logo.png"))
                .andExpect(jsonPath("$.tableCount").value(10));
    }

    @Test
    void updateMyRestaurant_withCnpj_shouldUpdateCnpj() throws Exception {
        String token = registerAndGetToken();
        String cnpj = String.valueOf(System.nanoTime());

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setCnpj(cnpj);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value(cnpj));
    }

    @Test
    void updateMyRestaurant_withCnpjAlreadyUsedByAnotherRestaurant_shouldReturn400() throws Exception {
        String cnpj = String.valueOf(System.nanoTime());
        registerRequest.setCnpj(cnpj);
        registerAndGetToken();

        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Pizza Place");
        registerRequest.setOwnerName("Owner 2");
        registerRequest.setOwnerEmail("owner2+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        String secondToken = registerAndGetToken();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setCnpj(cnpj);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyRestaurant_asManager_shouldSucceed() throws Exception {
        registerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();

        User manager = User.builder()
                .restaurant(owner.getRestaurant())
                .name("Manager")
                .email("manager+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.MANAGER)
                .active(true)
                .build();
        manager = userRepository.save(manager);

        String managerToken = jwtService.generateToken(new UserDetailsImpl(manager));

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setTableCount(20);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(20));
    }

    @Test
    void updateMyRestaurant_withNegativeTableCount_shouldReturn400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableCount\": -5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyRestaurant_withoutToken_shouldBeRejected() throws Exception {
        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setTradeName("Should not be saved");

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateMyRestaurant_withAutoPrintKitchenTickets_shouldPersistAndSurviveOmittedUpdate() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoPrintKitchenTickets").value(false));

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setAutoPrintKitchenTickets(true);
        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoPrintKitchenTickets").value(true));

        UpdateRestaurantRequest partialUpdate = new UpdateRestaurantRequest();
        partialUpdate.setTableCount(5);
        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(5))
                .andExpect(jsonPath("$.autoPrintKitchenTickets").value(true));
    }

    @Test
    void getMyRestaurant_shouldDefaultKitchenDelayThresholds() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenWarningThresholdMinutes").value(10))
                .andExpect(jsonPath("$.kitchenCriticalThresholdMinutes").value(20));
    }

    @Test
    void updateMyRestaurant_withKitchenDelayThresholds_shouldPersistAndSurviveOmittedUpdate() throws Exception {
        String token = registerAndGetToken();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setKitchenWarningThresholdMinutes(5);
        updateRequest.setKitchenCriticalThresholdMinutes(15);
        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenWarningThresholdMinutes").value(5))
                .andExpect(jsonPath("$.kitchenCriticalThresholdMinutes").value(15));

        UpdateRestaurantRequest partialUpdate = new UpdateRestaurantRequest();
        partialUpdate.setTableCount(5);
        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(5))
                .andExpect(jsonPath("$.kitchenWarningThresholdMinutes").value(5))
                .andExpect(jsonPath("$.kitchenCriticalThresholdMinutes").value(15));
    }

    @Test
    void updateMyRestaurant_withCriticalThresholdNotGreaterThanWarning_shouldReturn400() throws Exception {
        String token = registerAndGetToken();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setKitchenWarningThresholdMinutes(20);
        updateRequest.setKitchenCriticalThresholdMinutes(10);

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyRestaurant_withZeroWarningThreshold_shouldReturn400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kitchenWarningThresholdMinutes\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyRestaurant_asWaiter_shouldBeForbidden() throws Exception {
        registerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();

        User waiter = User.builder()
                .restaurant(owner.getRestaurant())
                .name("Waiter")
                .email("waiter+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.WAITER)
                .active(true)
                .build();
        waiter = userRepository.save(waiter);

        String waiterToken = jwtService.generateToken(new UserDetailsImpl(waiter));

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setTradeName("Should not be saved");

        mockMvc.perform(put("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadLogo_withValidJpeg_shouldReturnUrl() throws Exception {
        String ownerToken = registerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/restaurants/upload-logo")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/public/uploads/logos/")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".jpg")));
    }

    @Test
    void uploadLogo_withInvalidContentType_shouldReturn400() throws Exception {
        String ownerToken = registerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/restaurants/upload-logo")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadLogo_asWaiter_shouldBeForbidden() throws Exception {
        registerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();

        User waiter = User.builder()
                .restaurant(owner.getRestaurant())
                .name("Waiter")
                .email("waiter+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.WAITER)
                .active(true)
                .build();
        waiter = userRepository.save(waiter);
        String waiterToken = jwtService.generateToken(new UserDetailsImpl(waiter));

        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/restaurants/upload-logo")
                        .file(file)
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }
}
