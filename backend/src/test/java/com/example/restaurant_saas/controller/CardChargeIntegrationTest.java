package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.SaveCardIntegrationRequest;
import com.example.restaurant_saas.dto.request.SavePixIntegrationRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.request.VoidPaymentRequest;
import com.example.restaurant_saas.repository.CardChargeRepository;
import com.example.restaurant_saas.repository.CardIntegrationRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.MercadoPagoWebhookSignatureVerifier;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CardChargeService;
import com.example.restaurant_saas.service.MercadoPagoApiClient;
import com.example.restaurant_saas.service.WooviApiClient;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardChargeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardIntegrationRepository cardIntegrationRepository;

    @Autowired
    private CardChargeRepository cardChargeRepository;

    @Autowired
    private CardChargeService cardChargeService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private MercadoPagoApiClient mercadoPagoApiClient;

    @MockBean
    private WooviApiClient wooviApiClient;

    @MockBean
    private MercadoPagoWebhookSignatureVerifier signatureVerifier;

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

        // Signature verification itself is Mercado Pago's algorithm, not our logic - stub it valid
        // by default so webhook tests focus on our orchestration; the one test that cares about a
        // bad signature overrides this to false.
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(true);
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
        return TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(user));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    private String createTableAndGetId(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTableRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String openTabAndGetId(String token, String tableId) throws Exception {
        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult result = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createCategoryAndGetId(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers " + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProductAndGetId(String token, String categoryId, String name, String price) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createDeliveredItemTab(String token, String productPrice) throws Exception {
        return createDeliveredItemTabOnTable(token, createTableAndGetId(token), productPrice);
    }

    private String createDeliveredItemTabOnTable(String token, String tableId, String productPrice) throws Exception {
        String tabId = openTabAndGetId(token, tableId);
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Cheeseburger", productPrice);

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");

        for (ItemStatus status : List.of(ItemStatus.PREPARING, ItemStatus.READY, ItemStatus.DELIVERED)) {
            UpdateOrderItemStatusRequest statusRequest = new UpdateOrderItemStatusRequest();
            statusRequest.setStatus(status);
            mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusRequest)))
                    .andExpect(status().isOk());
        }

        return tabId;
    }

    private void saveCardIntegration(String token, String accessToken, String webhookSecret) throws Exception {
        SaveCardIntegrationRequest request = new SaveCardIntegrationRequest();
        request.setAccessToken(accessToken);
        request.setWebhookSecret(webhookSecret);
        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    private void stubCreatePreference(String initPoint) {
        when(mercadoPagoApiClient.createPreference(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MercadoPagoApiClient.PreferenceResult("pref-id", initPoint));
    }

    // The webhook body Mercado Pago actually sends is minimal by design - just a payment id, never
    // a status or external_reference (confirmed against developers.mercadopago.com during design).
    // Deliberately does not include any status field, so a test asserting on the outcome can only
    // be passing because CardChargeService#handleWebhook used the mocked getPayment() result, not
    // anything read from this body - the concrete regression test for the "never trust the webhook
    // body" security invariant.
    private String webhookBody(String paymentId) {
        return """
                {"action":"payment.updated","type":"payment","data":{"id":"%s"}}
                """.formatted(paymentId);
    }

    private UUID restaurantIdFor(String ownerEmail) {
        return userRepository.findByEmailBypassingRls(ownerEmail).orElseThrow().getRestaurant().getId();
    }

    @Test
    void integrationStatus_requiresBothAccessTokenAndWebhookSecret() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(get("/api/v1/card-integration").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");

        mockMvc.perform(get("/api/v1/card-integration").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void saveIntegration_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        SaveCardIntegrationRequest request = new SaveCardIntegrationRequest();
        request.setAccessToken("mp-access-token");
        request.setWebhookSecret("mp-webhook-secret");
        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + tokenFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveIntegration_partialUpdate_rotatesOnlyTheProvidedField() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token-v1", "mp-webhook-secret-v1");
        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        var integrationV1 = TenantTestSupport.withTenant(restaurantId, () -> cardIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow();

        // Only rotating the access token - webhookSecret left blank, so it should be untouched.
        SaveCardIntegrationRequest request = new SaveCardIntegrationRequest();
        request.setAccessToken("mp-access-token-v2");
        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        var integrationV2 = TenantTestSupport.withTenant(restaurantId, () -> cardIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow();
        assertNotEquals(integrationV1.getAccessTokenEncrypted(), integrationV2.getAccessTokenEncrypted());
        assertEquals(integrationV1.getWebhookSecretEncrypted(), integrationV2.getWebhookSecretEncrypted());
    }

    @Test
    void saveIntegration_bothBlankOnUpdate_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");

        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveCardIntegrationRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveIntegration_firstConnection_requiresBothFieldsTogether() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        SaveCardIntegrationRequest request = new SaveCardIntegrationRequest();
        request.setAccessToken("mp-access-token-only");
        mockMvc.perform(put("/api/v1/card-integration")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/card-integration").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void createCharge_withoutIntegrationConfigured_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCharge_thenApprovedWebhook_shouldClosePaidTabAndBeIdempotent() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(28.49))
                .andExpect(jsonPath("$.initPointUrl").value("https://mercadopago.com/checkout/pref-id"))
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        // getPayment() is the ONLY source of truth the webhook handler acts on - the body above
        // carries no status at all, so this outcome can only come from the mocked fetch.
        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-1"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult("mp-payment-1", "approved", "accredited", externalReference, new BigDecimal("28.49"), "credit_card"));

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .header("x-request-id", "req-1")
                        .content(webhookBody("mp-payment-1")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.amountPaid").value(28.49))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("CREDIT_CARD"));

        CardChargeStatus statusAfterFirstWebhook = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getStatus();
        assertEquals(CardChargeStatus.PAID, statusAfterFirstWebhook);

        // Mercado Pago retries webhooks - a second delivery of the same event must not
        // double-register the payment.
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .header("x-request-id", "req-1")
                        .content(webhookBody("mp-payment-1")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.amountPaid").value(28.49));
    }

    @Test
    void webhook_debitCard_mapsPaymentMethodFromPaymentTypeId() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-2"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult("mp-payment-2", "approved", "accredited", externalReference, new BigDecimal("28.49"), "debit_card"));

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .content(webhookBody("mp-payment-2")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("DEBIT_CARD"));
    }

    @Test
    void webhook_declined_shouldUnfreezeBillTotalAndExposeDeclineMessage() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-3"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult(
                        "mp-payment-3", "rejected", "cc_rejected_insufficient_amount", externalReference, new BigDecimal("28.49"), "credit_card"));

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .content(webhookBody("mp-payment-3")))
                .andExpect(status().isOk());

        // Tab stays OPEN, no payment registered, and billTotal is unfrozen again (same unfreeze
        // path as a cancelled Pix charge) - discount editing becomes allowed again.
        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyDiscountRequest())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DECLINED"))
                .andExpect(jsonPath("$[0].declineMessage").value("Saldo ou limite insuficiente. Tente outro cartão."));
    }

    @Test
    void webhook_withInvalidSignature_shouldReturn400AndNotTouchTheCharge() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(false);
        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=forged")
                        .content(webhookBody("mp-payment-x")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));

        verify(mercadoPagoApiClient, never()).getPayment(any(), any());
    }

    @Test
    void webhook_toWrongRestaurantPath_shouldNotCreditTheRealTenant() throws Exception {
        String ownerAToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerAToken, "mp-access-token-a", "mp-webhook-secret-a");
        String tabId = createDeliveredItemTab(ownerAToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerAToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");
        UUID restaurantAId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantAId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        // A second restaurant, also configured with card payment.
        RegisterRestaurantRequest secondRequest = new RegisterRestaurantRequest();
        secondRequest.setRestaurantName("Pizza Place");
        secondRequest.setPhone("11988888888");
        secondRequest.setAddress("Side St, 200");
        secondRequest.setOwnerName("Owner B");
        secondRequest.setOwnerEmail("ownerb+" + System.nanoTime() + "@test.com");
        secondRequest.setOwnerPassword("password123");
        registerRequest = secondRequest;
        String ownerBToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerBToken, "mp-access-token-b", "mp-webhook-secret-b");
        UUID restaurantBId = restaurantIdFor(secondRequest.getOwnerEmail());

        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-cross"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult("mp-payment-cross", "approved", "accredited", externalReference, new BigDecimal("28.49"), "credit_card"));

        // Deliver the webhook to restaurant B's path, even though the charge (and the
        // externalReference getPayment() echoes back) belongs to A - must be rejected as a no-op:
        // defense in depth, the found charge's restaurantId doesn't match the URL's.
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .content(webhookBody("mp-payment-cross")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));

        CardChargeStatus statusAfterCrossWebhook = TenantTestSupport.withTenant(restaurantAId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getStatus();
        assertEquals(CardChargeStatus.PENDING, statusAfterCrossWebhook);
    }

    @Test
    void refund_onVoid_shouldCallGatewayBeforeVoidingAndBeOrderedCorrectly() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-refund"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult("mp-payment-refund", "approved", "accredited", externalReference, new BigDecimal("28.49"), "credit_card"));
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .content(webhookBody("mp-payment-refund")))
                .andExpect(status().isOk());

        MvcResult tabResult = mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String paymentId = JsonPath.read(tabResult.getResponse().getContentAsString(), "$.payments[0].id");

        when(mercadoPagoApiClient.refundPayment(any(), eq("mp-payment-refund"))).thenReturn("refund-id-1");

        VoidPaymentRequest voidRequest = new VoidPaymentRequest();
        voidRequest.setReason("Customer complained");
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/payments/" + paymentId + "/void")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voidRequest)))
                .andExpect(status().isOk());

        verify(mercadoPagoApiClient, times(1)).refundPayment(any(), eq("mp-payment-refund"));

        CardChargeStatus statusAfterRefund = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getStatus();
        // Status itself is unrelated to the refund (still PAID - refund is tracked via
        // refundedAt/refundId, not a status change) - what matters is the void succeeded and the
        // gateway call happened exactly once.
        assertEquals(CardChargeStatus.PAID, statusAfterRefund);
    }

    @Test
    void refund_whenGatewayCallFails_shouldNotVoidThePayment() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.getPayment(any(), eq("mp-payment-refund-fail"))).thenReturn(
                new MercadoPagoApiClient.PaymentResult("mp-payment-refund-fail", "approved", "accredited", externalReference, new BigDecimal("28.49"), "credit_card"));
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/webhook/" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=irrelevant-stubbed-valid")
                        .content(webhookBody("mp-payment-refund-fail")))
                .andExpect(status().isOk());

        MvcResult tabResult = mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String paymentId = JsonPath.read(tabResult.getResponse().getContentAsString(), "$.payments[0].id");

        when(mercadoPagoApiClient.refundPayment(any(), eq("mp-payment-refund-fail")))
                .thenThrow(new com.example.restaurant_saas.exception.CardGatewayException("Mercado Pago is down"));

        VoidPaymentRequest voidRequest = new VoidPaymentRequest();
        voidRequest.setReason("Customer complained");
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/payments/" + paymentId + "/void")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voidRequest)))
                .andExpect(status().isBadGateway());

        // The payment must still be ACTIVE - a failed gateway refund must never leave the internal
        // ledger saying the money came back when it didn't.
        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(28.49));
    }

    @Test
    void createCardCharge_partialAmountLeavesRemainderForPixCharge_crossGatewayGuard() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        SavePixIntegrationRequest pixRequest = new SavePixIntegrationRequest();
        pixRequest.setAppId("woovi-app-id");
        mockMvc.perform(put("/api/v1/pix-integration")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest)))
                .andExpect(status().isNoContent());

        // 25.90 + 10% default service charge = 28.49 total.
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");
        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        // A card charge claims 18.49 of the total (split-comanda style), leaving exactly 10.00
        // genuinely uncommitted.
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 18.49}"))
                .andExpect(status().isOk());

        // A Pix charge for the remaining 10.00 succeeds...
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isOk());

        // ...but asking for even 0.01 more must be rejected: PixChargeService#createCharge's
        // remaining-balance check now sums PENDING card charges too, not just other Pix charges -
        // the cross-gateway guard this test exists to cover.
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0.01}"))
                .andExpect(status().isBadRequest());

        // Same guard in the other direction: CardChargeService#resolveAndValidateAmount sums
        // PENDING Pix charges too.
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0.01}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCharge_encryptsCredentialsAtRest_neverStoresRawValues() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String rawAccessToken = "mp-access-token-super-secret";
        String rawWebhookSecret = "mp-webhook-secret-super-secret";
        saveCardIntegration(ownerToken, rawAccessToken, rawWebhookSecret);

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        var integration = TenantTestSupport.withTenant(restaurantId,
                        () -> cardIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow();

        assertTrue(!integration.getAccessTokenEncrypted().contains(rawAccessToken),
                "the raw access token must never appear in the encrypted column");
        assertTrue(!integration.getWebhookSecretEncrypted().contains(rawWebhookSecret),
                "the raw webhook secret must never appear in the encrypted column");
    }

    @Test
    void publicMenu_exposesCardConfigured_onlyOnceIntegrationIsSaved() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MvcResult slugResult = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String slug = JsonPath.read(slugResult.getResponse().getContentAsString(), "$.slug");
        String tableId = createTableAndGetId(ownerToken);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.cardConfigured").value(false));

        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.cardConfigured").value(true));
    }

    @Test
    void customerCreateCharge_fromDigitalMenu_shouldGenerateChargeAndBeConfirmableByWebhook() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MvcResult slugResult = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String slug = JsonPath.read(slugResult.getResponse().getContentAsString(), "$.slug");
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tableId = createTableAndGetId(ownerToken);
        createDeliveredItemTabOnTable(ownerToken, tableId, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/card-charges"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(28.49))
                .andExpect(jsonPath("$.initPointUrl").value("https://mercadopago.com/checkout/pref-id"));
    }

    @Test
    void verify_byExternalReference_shouldClosePaidTabWithoutAnyWebhook() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        // Same backstop CardPaymentReturnPage triggers when the paying browser lands back on our
        // redirect - here the webhook never arrives at all (the exact sandbox failure mode this
        // path exists for), only the search-by-external_reference call resolves the payment.
        when(mercadoPagoApiClient.searchPaymentByExternalReference(any(), eq(externalReference))).thenReturn(
                new MercadoPagoApiClient.PaymentResult(
                        "mp-payment-verify", "approved", "accredited", externalReference, new BigDecimal("28.49"), "credit_card"));

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/verify/" + externalReference))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.amountPaid").value(28.49))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("CREDIT_CARD"));

        // Idempotent, same as the webhook: a second call (e.g. the customer's browser retrying)
        // must not double-register the payment.
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/verify/" + externalReference))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1));
    }

    @Test
    void verify_forUnknownOrAlreadyResolvedReference_shouldBeANoOp() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        // No charge at all behind this reference.
        mockMvc.perform(post("/api/v1/public/payments/mercadopago/verify/" + UUID.randomUUID()))
                .andExpect(status().isOk());

        verify(mercadoPagoApiClient, never()).searchPaymentByExternalReference(any(), any());

        // A charge that Mercado Pago hasn't approved (or even started) yet: the search call
        // legitimately returns nothing, and the charge must stay untouched.
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");
        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");
        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.searchPaymentByExternalReference(any(), eq(externalReference))).thenReturn(null);

        mockMvc.perform(post("/api/v1/public/payments/mercadopago/verify/" + externalReference))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));
    }

    // Regression test for the split-bill double-credit race (docs/CARD_PAYMENT.md,
    // V65__card_charge_concurrency_guards.sql): two callers hitting the same PENDING charge at
    // once (the async webhook racing the browser-triggered verify call, or two verify calls)
    // should never both register a Payment, even when the tab has enough uncommitted balance
    // elsewhere (a split-bill scenario) that a naive remaining-balance check alone wouldn't catch
    // it. The DB-level lock added in V65 (FOR UPDATE inside card_charge_by_external_reference)
    // serializes the two calls; the second one re-reads the now-PAID status and no-ops instead of
    // registering a second payment.
    @Test
    void verify_concurrentCallsOnSplitBillCharge_shouldNeverDoubleCreditTheTab() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        // 25.90 + 10% service charge = 28.49 total - a card charge for less than that leaves a
        // real, uncommitted "sibling portion" of the tab, exactly the headroom the race needs.
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        stubCreatePreference("https://mercadopago.com/checkout/pref-id");

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 14.00}"))
                .andExpect(status().isOk())
                .andReturn();
        String cardChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        UUID restaurantId = restaurantIdFor(registerRequest.getOwnerEmail());
        String externalReference = TenantTestSupport.withTenant(restaurantId,
                        () -> cardChargeRepository.findById(UUID.fromString(cardChargeId)))
                .orElseThrow().getExternalReference();

        when(mercadoPagoApiClient.searchPaymentByExternalReference(any(), eq(externalReference))).thenReturn(
                new MercadoPagoApiClient.PaymentResult(
                        "mp-payment-race", "approved", "accredited", externalReference, new BigDecimal("14.00"), "credit_card"));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    cardChargeService.verifyPendingChargeByExternalReference(externalReference);
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.amountPaid").value(14.00));
    }

    // Regression test for a real bug found testing this session: cancelling a pending card charge
    // on a tab that already has a real, active payment landed on it (a split-comanda tab partially
    // paid via Pix, this card charge covering the remainder) used to throw an unhandled 500
    // ("A payment has already been registered for this tab.") straight to the Caixa UI, because
    // CardChargeService#cancelPendingCharge called the unconditional TabService#unfreezeBillTotal.
    // Cancelling must succeed and simply leave the total frozen, since the Pix payment still counts
    // on it.
    @Test
    void cancelPendingCharge_onTabWithAnActivePartialPayment_shouldSucceedAndKeepTotalFrozen() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveCardIntegration(ownerToken, "mp-access-token", "mp-webhook-secret");
        // 25.90 + 10% service charge = 28.49 total.
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        RegisterPaymentsRequest partialPixPayment = new RegisterPaymentsRequest();
        PaymentEntryRequest pixEntry = new PaymentEntryRequest();
        pixEntry.setPaymentMethod(PaymentMethod.PIX);
        pixEntry.setAmount(new BigDecimal("14.00"));
        partialPixPayment.setPayments(List.of(pixEntry));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialPixPayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(14.00));

        stubCreatePreference("https://mercadopago.com/checkout/pref-id");
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(14.49));

        // This used to 500 - now it must cancel cleanly.
        mockMvc.perform(delete("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // The Pix payment is still active, so the total must stay frozen and OPEN - not reset to
        // editable, and definitely not lost.
        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(14.00))
                .andExpect(jsonPath("$.remainingBalance").value(14.49));

        mockMvc.perform(get("/api/v1/tabs/" + tabId + "/card-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // CANCELLED rows are dead, omitted from the list.
    }

    @Test
    void customerCreateCharge_withoutIntegrationConfigured_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MvcResult slugResult = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String slug = JsonPath.read(slugResult.getResponse().getContentAsString(), "$.slug");
        String tableId = createTableAndGetId(ownerToken);
        createDeliveredItemTabOnTable(ownerToken, tableId, "25.90");

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/card-charges"))
                .andExpect(status().isForbidden());
    }
}
