package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CashWithdrawalRequest;
import com.example.restaurant_saas.dto.request.CloseCashRegisterRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenCashRegisterRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CashRegisterControllerIntegrationTest {

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

    private String openCashRegisterRequestBody(String openingAmount) throws Exception {
        OpenCashRegisterRequest request = new OpenCashRegisterRequest();
        request.setOpeningAmount(new BigDecimal(openingAmount));
        return objectMapper.writeValueAsString(request);
    }

    private String closeCashRegisterRequestBody(String countedAmount, String closingNotes) throws Exception {
        CloseCashRegisterRequest request = new CloseCashRegisterRequest();
        request.setCountedAmount(new BigDecimal(countedAmount));
        request.setClosingNotes(closingNotes);
        return objectMapper.writeValueAsString(request);
    }

    private String withdrawalRequestBody(String amount, String reason) throws Exception {
        CashWithdrawalRequest request = new CashWithdrawalRequest();
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        request.setReason(reason);
        return objectMapper.writeValueAsString(request);
    }

    private void openCashRegister(String token, String openingAmount) throws Exception {
        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody(openingAmount)))
                .andExpect(status().isCreated());
    }

    private String createTableAndGetId(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    private String createOrderAndGetFirstItemId(String token, String tabId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    }

    private void deliverItem(String token, String itemId) throws Exception {
        updateItemStatus(token, itemId, ItemStatus.PREPARING);
        updateItemStatus(token, itemId, ItemStatus.READY);
        updateItemStatus(token, itemId, ItemStatus.DELIVERED);
    }

    private void updateItemStatus(String token, String itemId, ItemStatus status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(status);
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void payTabInCash(String token, String tabId, String paidAmount) throws Exception {
        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPaidAmount(new BigDecimal(paidAmount));
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void openSession_asOwner_shouldSucceedAndReturnOpenStatus() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openingAmount").value(100.00))
                .andExpect(jsonPath("$.expectedAmount").value(100.00))
                .andExpect(jsonPath("$.openedByName").value("Owner"));
    }

    @Test
    void openSession_whileAlreadyOpen_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("50.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentSession_withoutOpenSession_shouldReturn204() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(get("/api/v1/cash-register/current")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void getCurrentSession_afterCashPayment_shouldReflectExpectedAmount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        deliverItem(ownerToken, itemId);
        payTabInCash(ownerToken, tabId, "25.00");

        mockMvc.perform(get("/api/v1/cash-register/current")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAmount").value(125.00));
    }

    @Test
    void addWithdrawal_withOpenSession_shouldSucceedAndReduceExpectedAmount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/withdrawals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalRequestBody("30.00", "Levado ao cofre")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.reason").value("Levado ao cofre"))
                .andExpect(jsonPath("$.withdrawnByName").value("Owner"));

        mockMvc.perform(get("/api/v1/cash-register/current")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAmount").value(70.00))
                .andExpect(jsonPath("$.withdrawals", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void addWithdrawal_withoutReason_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/withdrawals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalRequestBody("30.00", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addWithdrawal_withoutOpenSession_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/cash-register/withdrawals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalRequestBody("30.00", "Levado ao cofre")))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeSession_withMatchingCountedAmount_shouldSucceedWithoutNotes() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("100.00", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.countedAmount").value(100.00))
                .andExpect(jsonPath("$.expectedAmount").value(100.00))
                .andExpect(jsonPath("$.differenceAmount").value(0));
    }

    @Test
    void closeSession_withDivergentAmountAndNoNotes_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("90.00", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeSession_withDivergentAmountAndNotes_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("90.00", "Faltou troco, conferido com a equipe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.differenceAmount").value(-10.00))
                .andExpect(jsonPath("$.closingNotes").value("Faltou troco, conferido com a equipe"));
    }

    @Test
    void closeSession_withoutOpenSession_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("0.00", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openAfterClose_shouldAllowANewSession() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");
        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("100.00", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("50.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingAmount").value(50.00));
    }

    @Test
    void listHistory_shouldReturnSessionsNewestFirst() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");
        mockMvc.perform(post("/api/v1/cash-register/close")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeCashRegisterRequestBody("100.00", null)))
                .andExpect(status().isOk());
        openCashRegister(ownerToken, "50.00");

        mockMvc.perform(get("/api/v1/cash-register/sessions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].openingAmount").value(50.00))
                .andExpect(jsonPath("$[1].status").value("CLOSED"))
                .andExpect(jsonPath("$[1].openingAmount").value(100.00));
    }

    @Test
    void payTab_withCashAndNoOpenSession_shouldReturn403() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        deliverItem(ownerToken, itemId);

        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPaidAmount(new BigDecimal("25.00"));
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payTab_withCashAfterOpeningSession_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        deliverItem(ownerToken, itemId);

        payTabInCash(ownerToken, tabId, "25.00");
    }

    @Test
    void payTab_withPixAndNoOpenSession_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        deliverItem(ownerToken, itemId);

        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.PIX);
        request.setPaidAmount(new BigDecimal("25.00"));
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void openSession_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("100.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void openSession_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User kitchen = createUserDirectly(owner, UserRole.KITCHEN);
        String kitchenToken = tokenFor(kitchen);

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("100.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void openSession_asCashier_shouldBeAllowed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User cashier = createUserDirectly(owner, UserRole.CASHIER);
        String cashierToken = tokenFor(cashier);

        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("100.00")))
                .andExpect(status().isCreated());
    }

    @Test
    void openSession_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/cash-register/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openCashRegisterRequestBody("100.00")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getCurrentSession_crossTenant_shouldNotLeakOtherRestaurantSession() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        openCashRegister(ownerToken, "100.00");

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        MvcResult otherResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherToken = JsonPath.read(otherResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/cash-register/current")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());
    }
}
