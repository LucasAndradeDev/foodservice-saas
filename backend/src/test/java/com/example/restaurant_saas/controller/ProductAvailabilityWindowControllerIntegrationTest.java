package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AvailabilityWindowRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductAvailabilityWindowControllerIntegrationTest {

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

    private String createCategoryAndGetId(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Pizzas " + System.nanoTime());
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

    private AvailabilityWindowRequest windowRequest(DayOfWeek dayOfWeek, String startTime, String endTime) {
        AvailabilityWindowRequest request = new AvailabilityWindowRequest();
        request.setDayOfWeek(dayOfWeek);
        request.setStartTime(LocalTime.parse(startTime));
        request.setEndTime(LocalTime.parse(endTime));
        return request;
    }

    @Test
    void createWindow_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SATURDAY, "11:00:00", "16:00:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.startTime").value("11:00:00"))
                .andExpect(jsonPath("$.endTime").value("16:00:00"));
    }

    @Test
    void createWindow_withStartAfterEnd_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(null, "16:00:00", "11:00:00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWindow_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Feijoada", "45.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SATURDAY, "11:00:00", "16:00:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWindows_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Feijoada", "45.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SATURDAY, "11:00:00", "16:00:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dayOfWeek").value("SATURDAY"));
    }

    @Test
    void updateWindow_shouldReplaceFields() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SATURDAY, "11:00:00", "16:00:00"))))
                .andExpect(status().isCreated())
                .andReturn();
        String windowId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/v1/products/" + productId + "/availability-windows/" + windowId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SUNDAY, "12:00:00", "15:00:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value("SUNDAY"))
                .andExpect(jsonPath("$.startTime").value("12:00:00"))
                .andExpect(jsonPath("$.endTime").value("15:00:00"));
    }

    @Test
    void deleteWindow_shouldRemoveIt() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(DayOfWeek.SATURDAY, "11:00:00", "16:00:00"))))
                .andExpect(status().isCreated())
                .andReturn();
        String windowId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/v1/products/" + productId + "/availability-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createOrder_withoutWindows_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Refrigerante", "6.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequestFor(productId))))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_outsideConfiguredWindow_shouldReturn403() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        // Full-day window on the day AFTER today: guaranteed to never match "now", regardless of current time.
        DayOfWeek anotherDay = LocalDate.now().plusDays(1).getDayOfWeek();
        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(anotherDay, "00:00:00", "23:59:59"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequestFor(productId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_insideConfiguredWindow_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Feijoada", "45.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        // Full-day window on today: always matches "now", regardless of the current time of day.
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(windowRequest(today, "00:00:00", "23:59:59"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequestFor(productId))))
                .andExpect(status().isCreated());
    }

    private CreateOrderRequest orderRequestFor(String productId) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        return request;
    }
}
