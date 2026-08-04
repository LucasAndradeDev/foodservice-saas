package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateModifierGroupRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.ModifierOptionInput;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateModifierGroupRequest;
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
class ProductModifierGroupControllerIntegrationTest {

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
        return userRepository.save(user);
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

    private void deliverItem(String token, String itemId) throws Exception {
        for (String status : new String[] {"PREPARING", "READY", "DELIVERED"}) {
            mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    private ModifierOptionInput option(String name, String priceDelta) {
        ModifierOptionInput option = new ModifierOptionInput();
        option.setName(name);
        option.setPriceDelta(new BigDecimal(priceDelta));
        return option;
    }

    private CreateModifierGroupRequest sizeGroupRequest() {
        CreateModifierGroupRequest request = new CreateModifierGroupRequest();
        request.setName("Tamanho");
        request.setSelectionType(ModifierSelectionType.SINGLE);
        request.setRequired(true);
        request.setOptions(List.of(option("P", "0.00"), option("G", "10.00")));
        return request;
    }

    @Test
    void createGroup_shouldSucceedAndReturnOptions() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tamanho"))
                .andExpect(jsonPath("$.selectionType").value("SINGLE"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[1].name").value("G"))
                .andExpect(jsonPath("$.options[1].priceDelta").value(10.00));
    }

    @Test
    void createGroup_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Pizza Calabresa", "40.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listGroups_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Pizza Calabresa", "40.00");

        mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tamanho"));
    }

    @Test
    void updateGroup_withNewOptions_shouldReplaceOptionList() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        UpdateModifierGroupRequest update = new UpdateModifierGroupRequest();
        update.setOptions(List.of(option("P", "0.00"), option("M", "5.00"), option("G", "10.00")));

        mockMvc.perform(put("/api/v1/products/" + productId + "/modifier-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.options[1].name").value("M"));
    }

    @Test
    void updateGroup_withEmptyOptions_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        UpdateModifierGroupRequest update = new UpdateModifierGroupRequest();
        update.setOptions(List.of());

        mockMvc.perform(put("/api/v1/products/" + productId + "/modifier-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options.length()").value(2));
    }

    @Test
    void deleteGroup_shouldRemoveIt() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/v1/products/" + productId + "/modifier-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createOrder_withSelectedOption_shouldAddPriceDeltaToSubtotal() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String largeOptionId = JsonPath.read(created.getResponse().getContentAsString(), "$.options[1].id");

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(2);
        item.setSelectedOptionIds(List.of(UUID.fromString(largeOptionId)));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].modifiers.length()").value(1))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("G"))
                .andExpect(jsonPath("$.items[0].subtotal").value(100.00))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void createOrder_missingRequiredGroupSelection_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated());

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_selectingTwoOptionsInSingleGroup_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        MvcResult created = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String smallOptionId = JsonPath.read(created.getResponse().getContentAsString(), "$.options[0].id");
        String largeOptionId = JsonPath.read(created.getResponse().getContentAsString(), "$.options[1].id");

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        item.setSelectedOptionIds(List.of(UUID.fromString(smallOptionId), UUID.fromString(largeOptionId)));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_withOptionFromAnotherProduct_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");
        String otherProductId = createProductAndGetId(token, categoryId, "Pizza Margherita", "38.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        MvcResult createdOnOther = mockMvc.perform(post("/api/v1/products/" + otherProductId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String otherProductOptionId = JsonPath.read(createdOnOther.getResponse().getContentAsString(), "$.options[0].id");

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        item.setSelectedOptionIds(List.of(UUID.fromString(otherProductOptionId)));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payTab_withDeliveredModifierItem_shouldAcceptTotalIncludingPriceDelta() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Pizza Calabresa", "40.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        MvcResult createdGroup = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sizeGroupRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String largeOptionId = JsonPath.read(createdGroup.getResponse().getContentAsString(), "$.options[1].id");

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        item.setSelectedOptionIds(List.of(UUID.fromString(largeOptionId)));
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setItems(List.of(item));

        MvcResult createdOrder = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(createdOrder.getResponse().getContentAsString(), "$.items[0].id");

        deliverItem(token, itemId);

        // Total must be 40.00 (base price) + 10.00 (size G delta) = 50.00, not the base price alone.
        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.PIX);
        entry.setAmount(new BigDecimal("50.00"));
        RegisterPaymentsRequest payRequest = new RegisterPaymentsRequest();
        payRequest.setPayments(List.of(entry));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(50.00));
    }

    @Test
    void createOrder_forProductWithoutModifiers_shouldSucceedWithoutSelections() throws Exception {
        String token = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(token);
        String productId = createProductAndGetId(token, categoryId, "Refrigerante", "6.00");
        String tableId = createTableAndGetId(token);
        String tabId = openTabAndGetId(token, tableId);

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].modifiers.length()").value(0))
                .andExpect(jsonPath("$.items[0].subtotal").value(6.00));
    }
}
