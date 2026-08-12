package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.SavePixIntegrationRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.repository.PixChargeRepository;
import com.example.restaurant_saas.repository.PixIntegrationRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.security.WooviWebhookSignatureVerifier;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
class PixChargeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PixIntegrationRepository pixIntegrationRepository;

    @Autowired
    private PixChargeRepository pixChargeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private WooviApiClient wooviApiClient;

    @MockBean
    private WooviWebhookSignatureVerifier signatureVerifier;

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

        // Signature verification itself is Woovi's algorithm, not our logic - stub it valid by
        // default so webhook tests focus on our orchestration; the one test that cares about a bad
        // signature overrides this to false.
        when(signatureVerifier.isValid(any(), any())).thenReturn(true);
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

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private void saveWooviIntegration(String token, String appId) throws Exception {
        SavePixIntegrationRequest request = new SavePixIntegrationRequest();
        request.setAppId(appId);
        mockMvc.perform(put("/api/v1/pix-integration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    private String webhookPayload(String correlationId, String status) {
        return """
                {"event":"OPENPIX:CHARGE_COMPLETED","charge":{"correlationID":"%s","status":"%s"}}
                """.formatted(correlationId, status);
    }

    @Test
    void integrationStatus_beforeAndAfterSaving_reflectsWhetherAppIdWasStored() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(get("/api/v1/pix-integration").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        saveWooviIntegration(ownerToken, "woovi-app-id-123");

        mockMvc.perform(get("/api/v1/pix-integration").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void saveIntegration_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        SavePixIntegrationRequest request = new SavePixIntegrationRequest();
        request.setAppId("woovi-app-id-123");
        mockMvc.perform(put("/api/v1/pix-integration")
                        .header("Authorization", "Bearer " + tokenFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCharge_withoutIntegrationConfigured_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCharge_withPendingItems_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setItems(List.of(item));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCharge_thenWebhookConfirmsIt_shouldClosePaidTabAndBeIdempotent() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        // 25.90 + the restaurant's default 10% service charge (enabled out of the box, see
        // Restaurant.serviceChargePercentage) - no serviceChargePercentage sent here, so it falls
        // back to the default regardless of caller (see createCharge_withOwnerOverride_shouldUseRequestedPercentage
        // for the OWNER/MANAGER override path, TabService#freezeBillTotalForPixCharge).
        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(28.49))
                .andExpect(jsonPath("$.brCode").value("00020126brcode"))
                .andReturn();
        String pixChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        // Editing items/discount is blocked once billTotal is frozen (same lock as the first real
        // payment) - the charge already generated a QR code for a specific amount.
        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.billTotal").value(28.49));

        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();
        String externalChargeId = TenantTestSupport.withTenant(restaurantId,
                        () -> pixChargeRepository.findById(UUID.fromString(pixChargeId)))
                .orElseThrow().getExternalChargeId();

        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "irrelevant-stubbed-valid")
                        .content(webhookPayload(externalChargeId, "COMPLETED")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.amountPaid").value(28.49))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.payments[0].createdByName").doesNotExist());

        PixChargeStatus statusAfterFirstWebhook = TenantTestSupport.withTenant(restaurantId,
                        () -> pixChargeRepository.findById(UUID.fromString(pixChargeId)))
                .orElseThrow().getStatus();
        assertEquals(PixChargeStatus.PAID, statusAfterFirstWebhook);

        // Woovi retries webhooks - a second delivery of the same event must not double-register
        // the payment or error out.
        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "irrelevant-stubbed-valid")
                        .content(webhookPayload(externalChargeId, "COMPLETED")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.amountPaid").value(28.49));
    }

    @Test
    void createCharge_withOwnerOverride_shouldUseRequestedPercentage() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        // Unlike the default-percentage case above, an OWNER/MANAGER may override the service
        // charge for a Pix charge they're generating from the Caixa, same as registerPayments -
        // 25.90 + 20% = 31.08, not the restaurant's default 10%.
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceChargePercentage\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(31.08));

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billTotal").value(31.08))
                .andExpect(jsonPath("$.serviceChargePercentage").value(20));
    }

    @Test
    void createCharge_withWaiterOverride_shouldIgnoreRequestedPercentageAndUseDefault() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        // A WAITER can still generate the QR code (same role gate as any other staff action here),
        // but isn't trusted to change the service charge - a requested override from this role is
        // silently ignored in favor of the restaurant's default 10%, same as registerPayments.
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + tokenFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceChargePercentage\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(28.49));
    }

    @Test
    void webhook_withInvalidSignature_shouldReturn400AndNotTouchTheCharge() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");
        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        when(signatureVerifier.isValid(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "forged")
                        .content(webhookPayload("whatever-correlation-id", "COMPLETED")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));
    }

    @Test
    void webhook_withUnknownCorrelationId_shouldReturn200AndDoNothing() throws Exception {
        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "irrelevant-stubbed-valid")
                        .content(webhookPayload(UUID.randomUUID().toString(), "COMPLETED")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelPixCharge_withPendingCharge_shouldUnfreezeTotalAndIgnoreLateWebhook() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        MvcResult chargeResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String pixChargeId = JsonPath.read(chargeResult.getResponse().getContentAsString(), "$.id");

        // Discount is refused while a Pix charge holds billTotal frozen (same lock the earlier
        // idempotency test exercises for a paid charge).
        ApplyDiscountRequest discountRequest = new ApplyDiscountRequest();
        discountRequest.setDiscountType(DiscountType.PERCENTAGE);
        discountRequest.setDiscountValue(new BigDecimal("10"));
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discountRequest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();
        PixChargeStatus statusAfterCancel = TenantTestSupport.withTenant(restaurantId,
                        () -> pixChargeRepository.findById(UUID.fromString(pixChargeId)))
                .orElseThrow().getStatus();
        assertEquals(PixChargeStatus.CANCELLED, statusAfterCancel);

        // billTotal is unfrozen now, so the same discount that was refused above goes through.
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discountRequest)))
                .andExpect(status().isOk());

        String externalChargeId = TenantTestSupport.withTenant(restaurantId,
                        () -> pixChargeRepository.findById(UUID.fromString(pixChargeId)))
                .orElseThrow().getExternalChargeId();

        // Woovi delivers a late webhook for the now-cancelled charge (customer scanned an old QR
        // code screenshot, for instance) - it must not resurrect a payment against a tab whose
        // total has since moved on.
        mockMvc.perform(post("/api/v1/public/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "irrelevant-stubbed-valid")
                        .content(webhookPayload(externalChargeId, "COMPLETED")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amountPaid").value(0));
    }

    @Test
    void cancelPixCharge_withNoPendingCharge_shouldBeNoOp() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        mockMvc.perform(delete("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelPixCharge_afterAPartialPaymentWasRegistered_shouldRefuse() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tabId = createDeliveredItemTab(ownerToken, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // A cashier settles part of the frozen total by card while the QR code is still pending -
        // cancelling the Pix charge after this must refuse, since that payment was computed against
        // the frozen total and unfreezing it now would leave the numbers inconsistent. CREDIT_CARD,
        // not CASH, so this doesn't also need an open cash register session (CashRegisterService).
        String paymentsBody = """
                {"payments":[{"paymentMethod":"CREDIT_CARD","amount":10.00}]}
                """;
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentsBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tabs/" + tabId + "/pix-charges")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCharge_encryptsAppIdAtRest_neverStoresRawValue() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String rawAppId = "woovi-app-id-super-secret";
        saveWooviIntegration(ownerToken, rawAppId);

        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();
        String storedValue = TenantTestSupport.withTenant(restaurantId,
                        () -> pixIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow().getApiKeyEncrypted();

        assertTrue(!storedValue.contains(rawAppId), "the raw AppID must never appear in the encrypted column");
    }

    @Test
    void publicMenu_exposesPixConfigured_onlyOnceIntegrationIsSaved() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTableAndGetId(ownerToken);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.pixConfigured").value(false));

        saveWooviIntegration(ownerToken, "woovi-app-id-123");

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.pixConfigured").value(true));
    }

    @Test
    void customerCreateCharge_fromDigitalMenu_shouldGenerateChargeAndBeConfirmableByWebhook() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tableId = createTableAndGetId(ownerToken);
        String tabId = createDeliveredItemTabOnTable(ownerToken, tableId, "25.90");

        when(wooviApiClient.createCharge(any(), any(), any(), any())).thenReturn(
                new WooviApiClient.ChargeResult("00020126brcode", "https://api.woovi.com/qr/abc.png", "https://openpix.com.br/pay/abc"));

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/pix-charges"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(28.49))
                .andExpect(jsonPath("$.brCode").value("00020126brcode"));

        mockMvc.perform(get("/api/v1/tabs/" + tabId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.billTotal").value(28.49));
    }

    @Test
    void customerCreateCharge_withNoDeliveredItems_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        saveWooviIntegration(ownerToken, "woovi-app-id-123");
        String tableId = createTableAndGetId(ownerToken);
        openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/pix-charges"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCreateCharge_withoutIntegrationConfigured_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTableAndGetId(ownerToken);
        createDeliveredItemTabOnTable(ownerToken, tableId, "25.90");

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/pix-charges"))
                .andExpect(status().isForbidden());
    }
}
