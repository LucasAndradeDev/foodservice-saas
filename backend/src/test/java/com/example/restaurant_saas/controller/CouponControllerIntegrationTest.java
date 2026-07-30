package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCouponRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateCouponRequest;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerIntegrationTest {

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

    private User createUserDirectly(User owner, UserRole role) {
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

    private String createCouponRequestBody(String code, DiscountType type, String value) throws Exception {
        CreateCouponRequest request = new CreateCouponRequest();
        request.setCode(code);
        request.setDiscountType(type);
        request.setDiscountValue(new BigDecimal(value));
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void createCoupon_asOwner_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("ANIVERSARIO10", DiscountType.PERCENTAGE, "10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ANIVERSARIO10"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.usedCount").value(0));
    }

    @Test
    void createCoupon_withDuplicateCodeCaseInsensitive_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("PROMO5", DiscountType.FIXED, "5.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("promo5", DiscountType.FIXED, "5.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCoupon_percentageOver100_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("EXAGERADO", DiscountType.PERCENTAGE, "150")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCoupon_withPastExpiration_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();

        CreateCouponRequest request = new CreateCouponRequest();
        request.setCode("VENCIDO");
        request.setDiscountType(DiscountType.FIXED);
        request.setDiscountValue(new BigDecimal("5.00"));
        request.setExpiresAt(OffsetDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCoupon_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("GARCOM10", DiscountType.PERCENTAGE, "10")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCoupon_asOwner_shouldToggleActiveAndChangeValue() throws Exception {
        String token = registerOwnerAndGetToken();
        MvcResult createResult = mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("EDITAVEL", DiscountType.PERCENTAGE, "10")))
                .andExpect(status().isCreated())
                .andReturn();
        String couponId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCouponRequest updateRequest = new UpdateCouponRequest();
        updateRequest.setDiscountValue(new BigDecimal("20"));
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountValue").value(20))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateCoupon_setExpiresAtAndMaxUses_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken();
        MvcResult createResult = mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("PRAZOEDITAVEL", DiscountType.FIXED, "5.00")))
                .andExpect(status().isCreated())
                .andReturn();
        String couponId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCouponRequest updateRequest = new UpdateCouponRequest();
        updateRequest.setExpiresAt(OffsetDateTime.now().plusDays(30));
        updateRequest.setMaxUses(50);

        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUses").value(50))
                .andExpect(jsonPath("$.expiresAt").exists());

        UpdateCouponRequest clearRequest = new UpdateCouponRequest();
        clearRequest.setClearExpiresAt(true);
        clearRequest.setClearMaxUses(true);

        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clearRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUses").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void updateCoupon_withPastExpiresAt_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        MvcResult createResult = mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("PRAZOPASSADO", DiscountType.FIXED, "5.00")))
                .andExpect(status().isCreated())
                .andReturn();
        String couponId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCouponRequest updateRequest = new UpdateCouponRequest();
        updateRequest.setExpiresAt(OffsetDateTime.now().minusDays(1));

        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listCoupons_asOwner_shouldReturnCreatedCoupon() throws Exception {
        String token = registerOwnerAndGetToken();
        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponRequestBody("LISTAGEM", DiscountType.FIXED, "3.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("LISTAGEM"));
    }
}
