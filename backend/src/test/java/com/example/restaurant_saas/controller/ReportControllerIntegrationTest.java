package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerIntegrationTest {

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

    private List<String> createOrderAndGetItemIds(String token, String tabId, String productId, int quantity) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(quantity);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return List.of((String) JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id"));
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

    private void payTab(String token, String tabId, String paymentMethod, String paidAmount) throws Exception {
        payTab(token, tabId, paymentMethod, paidAmount, null);
    }

    private void payTab(String token, String tabId, String paymentMethod, String paidAmount, String serviceChargePercentage) throws Exception {
        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        request.setPaidAmount(new BigDecimal(paidAmount));
        if (serviceChargePercentage != null) {
            request.setServiceChargePercentage(new BigDecimal(serviceChargePercentage));
        }
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_happyPath_shouldAggregateRevenueByPaymentMethodAndTopProducts() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "10.00");
        String sodaId = createProductAndGetId(ownerToken, categoryId, "Soda", "5.00");

        // Tab 1: 2 burgers + 1 soda, paid via PIX (25.00)
        String table1 = createTableAndGetId(ownerToken);
        String tab1 = openTabAndGetId(ownerToken, table1);
        String burgerItem1 = createOrderAndGetItemIds(ownerToken, tab1, burgerId, 2).get(0);
        String sodaItem1 = createOrderAndGetItemIds(ownerToken, tab1, sodaId, 1).get(0);
        deliverItem(ownerToken, burgerItem1);
        deliverItem(ownerToken, sodaItem1);
        payTab(ownerToken, tab1, "PIX", "25.00");

        // Tab 2: 1 burger, paid via CASH (10.00)
        String table2 = createTableAndGetId(ownerToken);
        String tab2 = openTabAndGetId(ownerToken, table2);
        String burgerItem2 = createOrderAndGetItemIds(ownerToken, tab2, burgerId, 1).get(0);
        deliverItem(ownerToken, burgerItem2);
        payTab(ownerToken, tab2, "CASH", "10.00");

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(35.00))
                .andExpect(jsonPath("$.closedTabsCount").value(2))
                .andExpect(jsonPath("$.averageTicket").value(17.50))
                .andExpect(jsonPath("$.byPaymentMethod", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.topProducts", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.topProducts[0].productName").value("Cheeseburger"))
                .andExpect(jsonPath("$.topProducts[0].quantitySold").value(3))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(30.00))
                .andExpect(jsonPath("$.topProducts[1].productName").value("Soda"))
                .andExpect(jsonPath("$.topProducts[1].quantitySold").value(1))
                .andExpect(jsonPath("$.topProducts[1].revenue").value(5.00));
    }

    @Test
    void getSummary_withServiceCharge_shouldExcludeItFromRevenue() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");

        String table1 = createTableAndGetId(ownerToken);
        String tab1 = openTabAndGetId(ownerToken, table1);
        String burgerItem1 = createOrderAndGetItemIds(ownerToken, tab1, burgerId, 1).get(0);
        deliverItem(ownerToken, burgerItem1);
        // 100.00 + 10% service charge = 110.00 paid, but only 100.00 is restaurant revenue
        payTab(ownerToken, tab1, "PIX", "110.00", "10");

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(100.00))
                .andExpect(jsonPath("$.closedTabsCount").value(1))
                .andExpect(jsonPath("$.byPaymentMethod[0].total").value(100.00));
    }

    @Test
    void getSummary_asManager_shouldBeAllowed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + managerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + waiterToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User kitchen = createUserDirectly(owner, UserRole.KITCHEN);
        String kitchenToken = tokenFor(kitchen);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_asCashier_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User cashier = createUserDirectly(owner, UserRole.CASHIER);
        String cashierToken = tokenFor(cashier);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_crossTenant_shouldNotLeakOtherRestaurantData() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "10.00");
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String itemId = createOrderAndGetItemIds(ownerToken, tabId, productId, 1).get(0);
        deliverItem(ownerToken, itemId);
        payTab(ownerToken, tabId, "PIX", "10.00");

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

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(0))
                .andExpect(jsonPath("$.closedTabsCount").value(0))
                .andExpect(jsonPath("$.topProducts", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getSummary_startAfterEnd_shouldReturnBadRequest() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", yesterday))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSummary_withoutToken_shouldBeRejected() throws Exception {
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().is4xxClientError());
    }
}
