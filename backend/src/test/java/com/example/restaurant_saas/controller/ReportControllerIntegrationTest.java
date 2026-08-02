package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenCashRegisterRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.SetMonthlyGoalRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.TabRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private TabRepository tabRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        // One test pays a tab in CASH; opening a session here keeps it green now that CASH
        // payments require an open cash register.
        openCashRegister(token);
        return token;
    }

    private void openCashRegister(String token) throws Exception {
        OpenCashRegisterRequest request = new OpenCashRegisterRequest();
        request.setOpeningAmount(new BigDecimal("100.00"));
        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private User createWaiterNamed(User owner, String name) {
        User user = User.builder()
                .restaurant(owner.getRestaurant())
                .name(name)
                .email(name.toLowerCase() + "+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.WAITER)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
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

    private void setProductCostPrice(String token, String productId, String costPrice) throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setCostPrice(new BigDecimal(costPrice));
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
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

    private void setTabPaidAt(String tabId, LocalDate paidDate) {
        Tab tab = tabRepository.findById(UUID.fromString(tabId)).orElseThrow();
        tab.setPaidAt(paidDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        tabRepository.save(tab);
    }

    private void createPaidTabOn(String token, String productId, String amount, LocalDate paidDate) throws Exception {
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);
        String itemId = createOrderAndGetItemIds(token, tabId, productId, 1).get(0);
        deliverItem(token, itemId);
        payTab(token, tabId, "PIX", amount);
        setTabPaidAt(tabId, paidDate);
    }

    @Test
    void getSummary_withComparison_shouldComputePercentageChange() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String product100 = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String product80 = createProductAndGetId(ownerToken, categoryId, "Soda", "80.00");

        createPaidTabOn(ownerToken, product100, "100.00", LocalDate.now());
        createPaidTabOn(ownerToken, product80, "80.00", LocalDate.now().minusDays(1));

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(100.00))
                .andExpect(jsonPath("$.comparison.previousTotalRevenue").value(80.00))
                .andExpect(jsonPath("$.comparison.previousClosedTabsCount").value(1))
                .andExpect(jsonPath("$.comparison.revenueChangePercentage").value(25.0))
                .andExpect(jsonPath("$.comparison.closedTabsChangePercentage").value(0.0))
                .andExpect(jsonPath("$.comparison.averageTicketChangePercentage").value(25.0));
    }

    @Test
    void getSummary_marchFullMonth_previousPeriodShouldBorrowDaysFromJanuary() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String product50 = createProductAndGetId(ownerToken, categoryId, "Item50", "50.00");
        String product999 = createProductAndGetId(ownerToken, categoryId, "Item999", "999.00");
        String product100 = createProductAndGetId(ownerToken, categoryId, "Item100", "100.00");

        // Previous period for March 1-31 is Jan 29 - Feb 28 (31 days, borrowing 3 from January).
        createPaidTabOn(ownerToken, product50, "50.00", LocalDate.of(2026, 1, 29));
        createPaidTabOn(ownerToken, product50, "50.00", LocalDate.of(2026, 2, 28));
        // One day before the previous period starts: must NOT be counted.
        createPaidTabOn(ownerToken, product999, "999.00", LocalDate.of(2026, 1, 28));
        // Inside the current period.
        createPaidTabOn(ownerToken, product100, "100.00", LocalDate.of(2026, 3, 1));

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", "2026-03-01")
                        .param("end", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(100.00))
                .andExpect(jsonPath("$.comparison.previousTotalRevenue").value(100.00))
                .andExpect(jsonPath("$.comparison.previousClosedTabsCount").value(2))
                .andExpect(jsonPath("$.comparison.revenueChangePercentage").value(0.0));
    }

    @Test
    void getSummary_noPreviousPeriodData_shouldReturnNullPercentages() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "50.00");

        createPaidTabOn(ownerToken, productId, "50.00", LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.previousClosedTabsCount").value(0))
                .andExpect(jsonPath("$.comparison.revenueChangePercentage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.comparison.closedTabsChangePercentage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.comparison.averageTicketChangePercentage").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void setMonthlyGoal_asOwner_shouldCreateAndReturnProgress() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "50.00");

        createPaidTabOn(ownerToken, productId, "50.00", LocalDate.of(2026, 7, 10));

        SetMonthlyGoalRequest goalRequest = new SetMonthlyGoalRequest();
        goalRequest.setMonth(LocalDate.of(2026, 7, 1));
        goalRequest.setRevenueGoal(new BigDecimal("100.00"));

        mockMvc.perform(put("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-07-01"))
                .andExpect(jsonPath("$.revenueGoal").value(100.00))
                .andExpect(jsonPath("$.currentRevenue").value(50.00))
                .andExpect(jsonPath("$.progressPercentage").value(50.0));

        mockMvc.perform(get("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("month", "2026-07-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-07-01"))
                .andExpect(jsonPath("$.revenueGoal").value(100.00))
                .andExpect(jsonPath("$.progressPercentage").value(50.0));
    }

    @Test
    void setMonthlyGoal_upsert_shouldUpdateExistingGoal() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        SetMonthlyGoalRequest first = new SetMonthlyGoalRequest();
        first.setMonth(LocalDate.of(2026, 8, 1));
        first.setRevenueGoal(new BigDecimal("1000.00"));
        mockMvc.perform(put("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        SetMonthlyGoalRequest updated = new SetMonthlyGoalRequest();
        updated.setMonth(LocalDate.of(2026, 8, 15));
        updated.setRevenueGoal(new BigDecimal("2000.00"));
        mockMvc.perform(put("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueGoal").value(2000.00));

        mockMvc.perform(get("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("month", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueGoal").value(2000.00));
    }

    @Test
    void getMonthlyGoal_withoutGoalSet_shouldReturnNullGoalAndPercentage() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(get("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("month", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueGoal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.progressPercentage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentRevenue").value(0));
    }

    @Test
    void setMonthlyGoal_withNonPositiveValue_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        SetMonthlyGoalRequest request = new SetMonthlyGoalRequest();
        request.setMonth(LocalDate.of(2026, 7, 1));
        request.setRevenueGoal(BigDecimal.ZERO);

        mockMvc.perform(put("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setMonthlyGoal_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        SetMonthlyGoalRequest request = new SetMonthlyGoalRequest();
        request.setMonth(LocalDate.of(2026, 7, 1));
        request.setRevenueGoal(new BigDecimal("1000.00"));

        mockMvc.perform(put("/api/v1/reports/goals")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
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
    void getSummary_withCostPrice_shouldComputeMargin() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.00");
        setProductCostPrice(ownerToken, burgerId, "9.50");
        String sodaId = createProductAndGetId(ownerToken, categoryId, "Soda", "5.00");

        String table1 = createTableAndGetId(ownerToken);
        String tab1 = openTabAndGetId(ownerToken, table1);
        String burgerItem1 = createOrderAndGetItemIds(ownerToken, tab1, burgerId, 2).get(0);
        String sodaItem1 = createOrderAndGetItemIds(ownerToken, tab1, sodaId, 1).get(0);
        deliverItem(ownerToken, burgerItem1);
        deliverItem(ownerToken, sodaItem1);
        payTab(ownerToken, tab1, "PIX", "55.00");

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProducts[0].productName").value("Cheeseburger"))
                .andExpect(jsonPath("$.topProducts[0].quantitySold").value(2))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(50.00))
                .andExpect(jsonPath("$.topProducts[0].costQuantityCovered").value(2))
                .andExpect(jsonPath("$.topProducts[0].costTotal").value(19.00))
                .andExpect(jsonPath("$.topProducts[0].marginTotal").value(31.00))
                .andExpect(jsonPath("$.topProducts[0].marginPercentage").value(62.0))
                .andExpect(jsonPath("$.topProducts[1].productName").value("Soda"))
                .andExpect(jsonPath("$.topProducts[1].costQuantityCovered").value(0))
                .andExpect(jsonPath("$.topProducts[1].marginPercentage").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getSummary_costPriceSetAfterSale_shouldOnlyCoverLaterUnits() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "20.00");

        String table1 = createTableAndGetId(ownerToken);
        String tab1 = openTabAndGetId(ownerToken, table1);

        String earlyItemId = createOrderAndGetItemIds(ownerToken, tab1, burgerId, 1).get(0);
        deliverItem(ownerToken, earlyItemId);

        setProductCostPrice(ownerToken, burgerId, "8.00");

        String laterItemId = createOrderAndGetItemIds(ownerToken, tab1, burgerId, 1).get(0);
        deliverItem(ownerToken, laterItemId);

        payTab(ownerToken, tab1, "PIX", "40.00");

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProducts[0].quantitySold").value(2))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(40.00))
                .andExpect(jsonPath("$.topProducts[0].costQuantityCovered").value(1))
                .andExpect(jsonPath("$.topProducts[0].costTotal").value(8.00))
                .andExpect(jsonPath("$.topProducts[0].marginTotal").value(12.00))
                .andExpect(jsonPath("$.topProducts[0].marginPercentage").value(60.0));
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

    private void setOrderCreatedAt(UUID orderId, java.time.OffsetDateTime createdAt) {
        // Order.createdAt is @Column(updatable = false), so Hibernate ignores it on UPDATE; go around it with raw SQL.
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?", createdAt, orderId);
    }

    private Double cellValue(String responseBody, java.time.DayOfWeek dayOfWeek, int hour, String field) {
        List<Object> values = JsonPath.read(responseBody,
                "$.cells[?(@.dayOfWeek=='" + dayOfWeek + "' && @.hour==" + hour + ")]." + field);
        return ((Number) values.get(0)).doubleValue();
    }

    @Test
    void getPeakHours_happyPath_shouldAggregateOccupancyAndOrdersByDayOfWeekAndHour() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "10.00");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String itemId = createOrderAndGetItemIds(ownerToken, tabId, burgerId, 1).get(0);
        deliverItem(ownerToken, itemId);
        payTab(ownerToken, tabId, "PIX", "10.00");

        LocalDate testDate = LocalDate.now().minusMonths(1);
        java.time.DayOfWeek testDayOfWeek = testDate.getDayOfWeek();
        ZoneId zone = ZoneId.systemDefault();

        Tab tab = tabRepository.findById(UUID.fromString(tabId)).orElseThrow();
        // Occupied from 11:30 to 13:15 -> covers hour buckets 11, 12 and 13.
        tab.setOpenedAt(testDate.atTime(11, 30).atZone(zone).toOffsetDateTime());
        tab.setClosedAt(testDate.atTime(13, 15).atZone(zone).toOffsetDateTime());
        tabRepository.save(tab);

        Order order = orderRepository.findByTabIdAndRestaurantId(tab.getId(), tab.getRestaurant().getId()).get(0);
        setOrderCreatedAt(order.getId(), testDate.atTime(12, 0).atZone(zone).toOffsetDateTime());

        MvcResult result = mockMvc.perform(get("/api/v1/reports/peak-hours")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", testDate.toString())
                        .param("end", testDate.toString()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertEquals(1.0, cellValue(body, testDayOfWeek, 11, "avgOccupiedTables"));
        assertEquals(0.0, cellValue(body, testDayOfWeek, 11, "avgOrderCount"));
        assertEquals(1.0, cellValue(body, testDayOfWeek, 12, "avgOccupiedTables"));
        assertEquals(1.0, cellValue(body, testDayOfWeek, 12, "avgOrderCount"));
        assertEquals(1.0, cellValue(body, testDayOfWeek, 13, "avgOccupiedTables"));
        assertEquals(0.0, cellValue(body, testDayOfWeek, 14, "avgOccupiedTables"));
        assertEquals(1.0, cellValue(body, testDayOfWeek, 0, "sampleCount"));
        assertEquals(0.0, cellValue(body, testDayOfWeek.plus(1), 0, "sampleCount"));
        assertEquals(0.0, cellValue(body, testDayOfWeek, 10, "avgOccupiedTables"));
    }

    @Test
    void getPeakHours_shouldExcludeCounterTabsFromOccupancyButKeepTheirOrders() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String burgerId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "10.00");

        OpenTabRequest openCounterRequest = new OpenTabRequest();
        openCounterRequest.setTableIds(List.of());
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openCounterRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String counterTabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        String itemId = createOrderAndGetItemIds(ownerToken, counterTabId, burgerId, 1).get(0);
        deliverItem(ownerToken, itemId);
        payTab(ownerToken, counterTabId, "PIX", "10.00");

        LocalDate testDate = LocalDate.now().minusMonths(1);
        java.time.DayOfWeek testDayOfWeek = testDate.getDayOfWeek();
        ZoneId zone = ZoneId.systemDefault();

        Tab tab = tabRepository.findById(UUID.fromString(counterTabId)).orElseThrow();
        tab.setOpenedAt(testDate.atTime(11, 30).atZone(zone).toOffsetDateTime());
        tab.setClosedAt(testDate.atTime(12, 15).atZone(zone).toOffsetDateTime());
        tabRepository.save(tab);

        Order order = orderRepository.findByTabIdAndRestaurantId(tab.getId(), tab.getRestaurant().getId()).get(0);
        setOrderCreatedAt(order.getId(), testDate.atTime(11, 30).atZone(zone).toOffsetDateTime());

        MvcResult result = mockMvc.perform(get("/api/v1/reports/peak-hours")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", testDate.toString())
                        .param("end", testDate.toString()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertEquals(0.0, cellValue(body, testDayOfWeek, 11, "avgOccupiedTables"));
        assertEquals(1.0, cellValue(body, testDayOfWeek, 11, "avgOrderCount"));
    }

    @Test
    void getPeakHours_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/peak-hours")
                        .header("Authorization", "Bearer " + waiterToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPeakHours_crossTenant_shouldNotLeakOtherRestaurantData() throws Exception {
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

        MvcResult result = mockMvc.perform(get("/api/v1/reports/peak-hours")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        List<Number> nonZeroCells = JsonPath.read(body, "$.cells[?(@.avgOccupiedTables > 0 || @.avgOrderCount > 0)]");
        assertEquals(0, nonZeroCells.size());
    }

    @Test
    void getPeakHours_startAfterEnd_shouldReturnBadRequest() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/v1/reports/peak-hours")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", yesterday))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWaiterPerformance_multipleWaitersOnSameTab_shouldAttributePerOrderNotPerTab() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User alice = createWaiterNamed(owner, "Alice");
        User bob = createWaiterNamed(owner, "Bob");
        String aliceToken = tokenFor(alice);
        String bobToken = tokenFor(bob);

        String categoryId = createCategoryAndGetId(ownerToken);
        String productA = createProductAndGetId(ownerToken, categoryId, "Burger", "20.00");
        String productB = createProductAndGetId(ownerToken, categoryId, "Soda", "10.00");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        // Same tab, two different waiters each launching their own order (shift change / help during rush).
        String itemAlice = createOrderAndGetItemIds(aliceToken, tabId, productA, 1).get(0);
        String itemBob = createOrderAndGetItemIds(bobToken, tabId, productB, 1).get(0);
        deliverItem(ownerToken, itemAlice);
        deliverItem(ownerToken, itemBob);

        payTab(ownerToken, tabId, "PIX", "30.00");
        setTabPaidAt(tabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.rows[0].waiterName").value("Alice"))
                .andExpect(jsonPath("$.rows[0].totalSales").value(20.00))
                .andExpect(jsonPath("$.rows[0].orderCount").value(1))
                .andExpect(jsonPath("$.rows[1].waiterName").value("Bob"))
                .andExpect(jsonPath("$.rows[1].totalSales").value(10.00))
                .andExpect(jsonPath("$.rows[1].orderCount").value(1));
    }

    @Test
    void getWaiterPerformance_selfServiceOrder_shouldAppearAsSeparateRow() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "15.00");
        String tableId = createTableAndGetId(ownerToken);

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest selfOrderRequest = new CreateOrderRequest();
        selfOrderRequest.setItems(List.of(item));

        MvcResult selfOrderResult = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfOrderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(selfOrderResult.getResponse().getContentAsString(), "$.tabId");
        String itemId = JsonPath.read(selfOrderResult.getResponse().getContentAsString(), "$.items[0].id");

        deliverItem(ownerToken, itemId);
        payTab(ownerToken, tabId, "PIX", "15.00");
        setTabPaidAt(tabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[0].waiterId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rows[0].waiterName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rows[0].totalSales").value(15.00));
    }

    @Test
    void getWaiterPerformance_waiterAndSelfServiceOnDifferentTabs_selfServiceRowShouldBeListedLast() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createWaiterNamed(owner, "Diana");
        String waiterToken = tokenFor(waiter);
        String slug = getSlug(ownerToken);

        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "50.00");

        // Waiter-served tab: higher total than the self-service one below.
        String waiterTableId = createTableAndGetId(ownerToken);
        String waiterTabId = openTabAndGetId(ownerToken, waiterTableId);
        String waiterItemId = createOrderAndGetItemIds(waiterToken, waiterTabId, productId, 1).get(0);
        deliverItem(ownerToken, waiterItemId);
        payTab(ownerToken, waiterTabId, "PIX", "50.00");
        setTabPaidAt(waiterTabId, LocalDate.now());

        // Self-service tab.
        String selfServiceTableId = createTableAndGetId(ownerToken);
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest selfOrderRequest = new CreateOrderRequest();
        selfOrderRequest.setItems(List.of(item));
        MvcResult selfOrderResult = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + selfServiceTableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfOrderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String selfServiceTabId = JsonPath.read(selfOrderResult.getResponse().getContentAsString(), "$.tabId");
        String selfServiceItemId = JsonPath.read(selfOrderResult.getResponse().getContentAsString(), "$.items[0].id");
        deliverItem(ownerToken, selfServiceItemId);
        payTab(ownerToken, selfServiceTabId, "PIX", "50.00");
        setTabPaidAt(selfServiceTabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.rows[0].waiterName").value("Diana"))
                .andExpect(jsonPath("$.rows[1].waiterName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getWaiterPerformance_cancelledItem_shouldNotCountTowardSalesOrOrderCount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createWaiterNamed(owner, "Carla");
        String waiterToken = tokenFor(waiter);

        String categoryId = createCategoryAndGetId(ownerToken);
        String deliveredProduct = createProductAndGetId(ownerToken, categoryId, "Burger", "20.00");
        String cancelledProduct = createProductAndGetId(ownerToken, categoryId, "Soda", "10.00");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        String deliveredItemId = createOrderAndGetItemIds(waiterToken, tabId, deliveredProduct, 1).get(0);
        String cancelledItemId = createOrderAndGetItemIds(waiterToken, tabId, cancelledProduct, 1).get(0);

        deliverItem(ownerToken, deliveredItemId);
        updateItemStatus(ownerToken, cancelledItemId, ItemStatus.CANCELLED);

        payTab(ownerToken, tabId, "PIX", "20.00");
        setTabPaidAt(tabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[0].waiterName").value("Carla"))
                .andExpect(jsonPath("$.rows[0].totalSales").value(20.00))
                .andExpect(jsonPath("$.rows[0].orderCount").value(1));
    }

    @Test
    void getWaiterPerformance_itemWithMultipleModifiers_shouldNotDoubleCountSales() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createWaiterNamed(owner, "Erica");
        String waiterToken = tokenFor(waiter);

        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Pizza", "40.00");

        com.example.restaurant_saas.dto.request.ModifierOptionInput bacon =
                new com.example.restaurant_saas.dto.request.ModifierOptionInput();
        bacon.setName("Bacon");
        bacon.setPriceDelta(new BigDecimal("5.00"));
        com.example.restaurant_saas.dto.request.ModifierOptionInput cheese =
                new com.example.restaurant_saas.dto.request.ModifierOptionInput();
        cheese.setName("Cheese");
        cheese.setPriceDelta(new BigDecimal("3.00"));

        com.example.restaurant_saas.dto.request.CreateModifierGroupRequest groupRequest =
                new com.example.restaurant_saas.dto.request.CreateModifierGroupRequest();
        groupRequest.setName("Extras");
        groupRequest.setSelectionType(com.example.restaurant_saas.domain.enums.ModifierSelectionType.MULTIPLE);
        groupRequest.setRequired(false);
        groupRequest.setOptions(List.of(bacon, cheese));

        MvcResult groupResult = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String baconOptionId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.options[0].id");
        String cheeseOptionId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.options[1].id");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        item.setSelectedOptionIds(List.of(UUID.fromString(baconOptionId), UUID.fromString(cheeseOptionId)));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult orderResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(48.00))
                .andReturn();
        String itemId = JsonPath.read(orderResult.getResponse().getContentAsString(), "$.items[0].id");

        deliverItem(ownerToken, itemId);
        payTab(ownerToken, tabId, "PIX", "48.00");
        setTabPaidAt(tabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[0].waiterName").value("Erica"))
                .andExpect(jsonPath("$.rows[0].totalSales").value(48.00))
                .andExpect(jsonPath("$.rows[0].orderCount").value(1));
    }

    @Test
    void getWaiterPerformance_transferredItem_shouldKeepOriginalWaiterCredit() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createWaiterNamed(owner, "Fernanda");
        String waiterToken = tokenFor(waiter);

        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "22.00");

        String sourceTableId = createTableAndGetId(ownerToken);
        String sourceTabId = openTabAndGetId(ownerToken, sourceTableId);
        String itemId = createOrderAndGetItemIds(waiterToken, sourceTabId, productId, 1).get(0);

        String targetTableId = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, targetTableId);

        com.example.restaurant_saas.dto.request.TransferItemsRequest transferRequest =
                new com.example.restaurant_saas.dto.request.TransferItemsRequest();
        transferRequest.setItemIds(List.of(UUID.fromString(itemId)));
        transferRequest.setTargetTabId(UUID.fromString(targetTabId));
        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk());

        deliverItem(ownerToken, itemId);
        payTab(ownerToken, targetTabId, "PIX", "22.00");
        setTabPaidAt(targetTabId, LocalDate.now());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[0].waiterName").value("Fernanda"))
                .andExpect(jsonPath("$.rows[0].totalSales").value(22.00));
    }

    @Test
    void getWaiterPerformance_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + waiterToken)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWaiterPerformance_startAfterEnd_shouldReturnBadRequest() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/v1/reports/waiters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("start", today)
                        .param("end", yesterday))
                .andExpect(status().isBadRequest());
    }
}
