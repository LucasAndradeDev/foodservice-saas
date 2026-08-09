package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Coupon;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.dto.request.CreateCouponRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateCouponRequest;
import com.example.restaurant_saas.repository.CouponRepository;
import com.example.restaurant_saas.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicCouponControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

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

    private String createTable(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTableRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createCoupon(String token, String code, DiscountType type, String value, Integer maxUses) throws Exception {
        CreateCouponRequest request = new CreateCouponRequest();
        request.setCode(code);
        request.setDiscountType(type);
        request.setDiscountValue(new BigDecimal(value));
        request.setMaxUses(maxUses);
        MvcResult result = mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deactivateCoupon(String token, String couponId) throws Exception {
        UpdateCouponRequest request = new UpdateCouponRequest();
        request.setActive(false);
        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void backdateCouponExpiration(String couponId) {
        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();
        TenantTestSupport.withTenant(restaurantId, () -> {
            Coupon coupon = couponRepository.findById(UUID.fromString(couponId)).orElseThrow();
            coupon.setExpiresAt(OffsetDateTime.now().minusDays(1));
            couponRepository.save(coupon);
        });
    }

    @Test
    void redeem_happyPath_shouldApplyDiscountAndExposeLabel() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        createCoupon(token, "ANIVERSARIO10", DiscountType.PERCENTAGE, "10", null);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"aniversario10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAppliedLabel").value("Cupom: ANIVERSARIO10 · -10%"));

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.discountAppliedLabel").value("Cupom: ANIVERSARIO10 · -10%"));
    }

    @Test
    void redeem_withUnknownCode_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NAOEXISTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeem_withInactiveCoupon_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        String couponId = createCoupon(token, "DESLIGADO", DiscountType.FIXED, "5.00", null);
        deactivateCoupon(token, couponId);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DESLIGADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeem_withExpiredCoupon_shouldReturn403() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        String couponId = createCoupon(token, "EXPIRADO", DiscountType.FIXED, "5.00", null);
        backdateCouponExpiration(couponId);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EXPIRADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_pastMaxUses_shouldReturn403OnSecondTable() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String firstTableId = createTable(token);
        String secondTableId = createTable(token);
        createCoupon(token, "LIMITADO", DiscountType.FIXED, "5.00", 1);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + firstTableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIMITADO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + secondTableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIMITADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_sameCodeTwiceOnSameTable_shouldBeIdempotentAndNotDoubleCountUsage() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        String couponId = createCoupon(token, "REPETIDO", DiscountType.FIXED, "5.00", 1);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"REPETIDO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"repetido\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + couponId + "')].usedCount").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void redeem_afterLoweringMaxUsesBelowUsedCount_shouldBeRejectedImmediately() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String firstTableId = createTable(token);
        String secondTableId = createTable(token);
        String couponId = createCoupon(token, "REDUZIDO", DiscountType.FIXED, "5.00", 5);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + firstTableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"REDUZIDO\"}"))
                .andExpect(status().isOk());

        UpdateCouponRequest lowerLimit = new UpdateCouponRequest();
        lowerLimit.setMaxUses(1);
        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lowerLimit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUses").value(1));

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + secondTableId + "/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"REDUZIDO\"}"))
                .andExpect(status().isForbidden());
    }
}
