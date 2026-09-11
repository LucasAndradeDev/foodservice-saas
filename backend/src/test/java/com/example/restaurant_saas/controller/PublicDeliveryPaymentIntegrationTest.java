package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.SaveCardIntegrationRequest;
import com.example.restaurant_saas.dto.request.SavePixIntegrationRequest;
import com.example.restaurant_saas.repository.PixChargeRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.security.WooviWebhookSignatureVerifier;
import com.example.restaurant_saas.service.MercadoPagoApiClient;
import com.example.restaurant_saas.service.WooviApiClient;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Task 29.1: a delivery order is paid immediately at submission (Pix/card), before the kitchen
// starts - unlike the dine-in flow (PixChargeIntegrationTest), there is no DELIVERED-item gate.
@SpringBootTest
@AutoConfigureMockMvc
class PublicDeliveryPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PixChargeRepository pixChargeRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @MockBean
    private WooviApiClient wooviApiClient;

    @MockBean
    private WooviWebhookSignatureVerifier signatureVerifier;

    @MockBean
    private MercadoPagoApiClient mercadoPagoApiClient;

    @BeforeEach
    void setUp() {
        when(signatureVerifier.isValid(any(), any())).thenReturn(true);
    }

    private record OwnerSession(String token, UUID restaurantId) {
    }

    // A new signup is unapproved by default (AuthService#registerRestaurant) and can't log in -
    // approve it directly then log in to get a working token, since registration itself no
    // longer hands one out.
    private OwnerSession registerOwner() throws Exception {
        RegisterRestaurantRequest registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID restaurantId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.restaurant.id"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElseThrow();
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
        return new OwnerSession(
                JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken"),
                restaurantId);
    }

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private void saveWooviIntegration(String token) throws Exception {
        SavePixIntegrationRequest request = new SavePixIntegrationRequest();
        request.setAppId("woovi-app-id-123");
        mockMvc.perform(put("/api/v1/pix-integration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    private record DeliveryOrder(String tabId, String accessToken) {
    }

    // Cheeseburger (25.90) + the Centro zone's fee (8.00) = 33.90 - no service charge on delivery
    // orders (2026-08-18 decision, TabService#resolveBillTotal), same math as
    // DeliveryControllerIntegrationTest#payTabInFull.
    private DeliveryOrder createDeliveryOrder(String token, String slug) throws Exception {
        CreateCategoryRequest categoryRequest = new CreateCategoryRequest();
        categoryRequest.setName("Burgers");
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id");

        CreateProductRequest productRequest = new CreateProductRequest();
        productRequest.setName("Cheeseburger");
        productRequest.setPrice(new BigDecimal("25.90"));
        productRequest.setCategoryId(UUID.fromString(categoryId));
        MvcResult productResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(productResult.getResponse().getContentAsString(), "$.id");

        CreateDeliveryZoneRequest zoneRequest = new CreateDeliveryZoneRequest();
        zoneRequest.setNeighborhood("Centro");
        zoneRequest.setFee(new BigDecimal("8.00"));
        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zoneRequest)))
                .andExpect(status().isCreated());

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("items", objectMapper.valueToTree(List.of(item)));
        body.put("customerName", "Maria Souza");
        // Unique per call, zero-padded to a fixed width - CreateDeliveryOrderRequest#customerPhone
        // requires 10-15 digits, and an unpadded nanoTime remainder occasionally comes up short,
        // intermittently failing validation with "Invalid phone number".
        body.put("customerPhone", "1199" + String.format("%07d", System.nanoTime() % 10_000_000L));
        body.put("street", "Rua das Flores");
        body.put("number", "123");
        body.put("neighborhood", "Centro");
        body.put("city", "Sao Paulo");

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return new DeliveryOrder(JsonPath.read(content, "$.tabId"), JsonPath.read(content, "$.accessToken"));
    }

    private String webhookPayload(String correlationId, String status) {
        return """
                {"event":"OPENPIX:CHARGE_COMPLETED","charge":{"correlationID":"%s","status":"%s"}}
                """.formatted(correlationId, status);
    }

    @Test
    void createDeliveryPixCharge_beforeKitchenStarts_succeedsAndWebhookClosesTheTab() throws Exception {
        OwnerSession owner = registerOwner();
        String slug = getSlug(owner.token());
        saveWooviIntegration(owner.token());
        DeliveryOrder order = createDeliveryOrder(owner.token(), slug);

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        // Items are still PENDING at this point - unlike the dine-in flow, that must not block it.
        MvcResult chargeResult = mockMvc.perform(post("/api/v1/public/deliveries/" + order.accessToken() + "/pix-charges"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(33.90))
                .andExpect(jsonPath("$.brCode").value("00020126brcode"))
                .andReturn();
        String pixChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/public/deliveries/" + order.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(false))
                .andExpect(jsonPath("$.deliveryFee").value(8.00));

        String externalChargeId = TenantTestSupport.withTenant(owner.restaurantId(),
                        () -> pixChargeRepository.findById(UUID.fromString(pixChargeId)))
                .orElseThrow().getExternalChargeId();

        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "irrelevant-stubbed-valid")
                        .content(webhookPayload(externalChargeId, "COMPLETED")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/deliveries/" + order.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));

        mockMvc.perform(get("/api/v1/tabs/" + order.tabId()).header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.amountPaid").value(33.90));
    }

    @Test
    void createDeliveryCardCharge_beforeKitchenStarts_returnsCheckoutLink() throws Exception {
        OwnerSession owner = registerOwner();
        String slug = getSlug(owner.token());
        SaveCardIntegrationRequest cardRequest = new SaveCardIntegrationRequest();
        cardRequest.setAccessToken("mp-access-token");
        cardRequest.setWebhookSecret("mp-webhook-secret");
        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardRequest)))
                .andExpect(status().isNoContent());
        DeliveryOrder order = createDeliveryOrder(owner.token(), slug);

        when(mercadoPagoApiClient.createPreference(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MercadoPagoApiClient.PreferenceResult("pref-id", "https://mercadopago.com/checkout/pref-id"));

        mockMvc.perform(post("/api/v1/public/deliveries/" + order.accessToken() + "/card-charges"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(33.90))
                .andExpect(jsonPath("$.initPointUrl").value("https://mercadopago.com/checkout/pref-id"));
    }

    @Test
    void createDeliveryPixCharge_withoutIntegrationConfigured_shouldReturn403() throws Exception {
        OwnerSession owner = registerOwner();
        String slug = getSlug(owner.token());
        DeliveryOrder order = createDeliveryOrder(owner.token(), slug);

        mockMvc.perform(post("/api/v1/public/deliveries/" + order.accessToken() + "/pix-charges"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDeliveryPixCharge_forUnknownToken_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/public/deliveries/" + UUID.randomUUID() + "/pix-charges"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDeliveryStatus_forUnknownToken_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/public/deliveries/" + UUID.randomUUID() + "/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDeliveryStatus_forAnotherRestaurantsToken_stillOnlyReturnsItsOwnData() throws Exception {
        OwnerSession ownerA = registerOwner();
        String slugA = getSlug(ownerA.token());
        DeliveryOrder orderA = createDeliveryOrder(ownerA.token(), slugA);

        OwnerSession ownerB = registerOwner();
        String slugB = getSlug(ownerB.token());
        DeliveryOrder orderB = createDeliveryOrder(ownerB.token(), slugB);

        mockMvc.perform(get("/api/v1/public/deliveries/" + orderA.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabId").value(orderA.tabId()));

        mockMvc.perform(get("/api/v1/public/deliveries/" + orderB.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabId").value(orderB.tabId()));
    }
}
