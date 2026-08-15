package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.MenuImportCommitRequest;
import com.example.restaurant_saas.dto.request.MenuImportProductItem;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.exception.MenuImportProcessingException;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.GeminiCategory;
import com.example.restaurant_saas.service.GeminiExtractionResult;
import com.example.restaurant_saas.service.GeminiProduct;
import com.example.restaurant_saas.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MenuImportControllerIntegrationTest {

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

    @MockBean
    private GeminiService geminiService;

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

    private String createCategory(String ownerToken, String name) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String ownerToken, String categoryId, String name, String price) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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

    private MockMultipartFile validXlsxFile() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Cardápio");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Coca-Cola");
            row.createCell(1).setCellValue("5.90");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "menu.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private MockMultipartFile validImageFile() {
        return new MockMultipartFile("files", "menu.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
    }

    private MockMultipartFile emptyXlsxFile() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Vazia");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "menu.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void extract_withValidXlsxAndStubbedGemini_shouldFlagMatchedCategoryAndDuplicateProduct() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String bebidasId = createCategory(ownerToken, "Bebidas");
        createProduct(ownerToken, bebidasId, "Coca-Cola", "5.00");

        when(geminiService.extractMenu(anyString(), anyList())).thenReturn(new GeminiExtractionResult(List.of(
                new GeminiCategory("Bebidas", List.of(
                        new GeminiProduct("Coca-Cola", "Refrigerante gelado", "5.90"),
                        new GeminiProduct("Suco de Laranja", null, "8.00")
                ))
        )));

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(validXlsxFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Bebidas"))
                .andExpect(jsonPath("$.categories[0].matchedCategoryId").value(bebidasId))
                .andExpect(jsonPath("$.products[0].name").value("Coca-Cola"))
                .andExpect(jsonPath("$.products[0].duplicate").value(true))
                .andExpect(jsonPath("$.products[1].name").value("Suco de Laranja"))
                .andExpect(jsonPath("$.products[1].duplicate").value(false));
    }

    @Test
    void extract_shouldPassExistingActiveCategoryNamesToGemini() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createCategory(ownerToken, "Hambúrguer");
        createCategory(ownerToken, "Bebidas");

        when(geminiService.extractMenu(anyString(), anyList())).thenReturn(new GeminiExtractionResult(List.of(
                new GeminiCategory("Lanches", List.of(new GeminiProduct("X-Salada", null, "22.90")))
        )));

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(validXlsxFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        verify(geminiService).extractMenu(anyString(), argThat(names ->
                names.containsAll(List.of("Hambúrguer", "Bebidas")) && names.size() == 2));
    }

    @Test
    void extract_withNonXlsxFile_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "menu.csv", "text/csv", "a,b,c".getBytes());

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extract_whenGeminiThrowsProcessingException_shouldReturn422() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        when(geminiService.extractMenu(anyString(), anyList()))
                .thenThrow(new MenuImportProcessingException("Falha ao consultar o serviço de IA."));

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(validXlsxFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void extract_withEmptySpreadsheet_shouldReturn422AndNeverCallGemini() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(emptyXlsxFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(geminiService);
    }

    @Test
    void extract_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(multipart("/api/v1/menu-import/extract")
                        .file(validXlsxFile())
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void extractDocument_withValidImageAndStubbedGemini_shouldFlagMatchedCategoryAndDuplicateProduct() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String bebidasId = createCategory(ownerToken, "Bebidas");
        createProduct(ownerToken, bebidasId, "Coca-Cola", "5.00");

        when(geminiService.extractMenuFromDocuments(anyList(), anyList())).thenReturn(new GeminiExtractionResult(List.of(
                new GeminiCategory("Bebidas", List.of(
                        new GeminiProduct("Coca-Cola", "Refrigerante gelado", "5.90"),
                        new GeminiProduct("Suco de Laranja", null, "8.00")
                ))
        )));

        mockMvc.perform(multipart("/api/v1/menu-import/extract-document")
                        .file(validImageFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Bebidas"))
                .andExpect(jsonPath("$.categories[0].matchedCategoryId").value(bebidasId))
                .andExpect(jsonPath("$.products[0].name").value("Coca-Cola"))
                .andExpect(jsonPath("$.products[0].duplicate").value(true))
                .andExpect(jsonPath("$.products[1].name").value("Suco de Laranja"))
                .andExpect(jsonPath("$.products[1].duplicate").value(false));
    }

    @Test
    void extractDocument_withMultiplePhotos_shouldSendAllToGemini() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        when(geminiService.extractMenuFromDocuments(anyList(), anyList())).thenReturn(new GeminiExtractionResult(List.of(
                new GeminiCategory("Lanches", List.of(new GeminiProduct("X-Salada", null, "22.90")))
        )));

        mockMvc.perform(multipart("/api/v1/menu-import/extract-document")
                        .file(new MockMultipartFile("files", "pagina1.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("files", "pagina2.png", "image/png", new byte[]{2}))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        verify(geminiService).extractMenuFromDocuments(argThat(documents -> documents.size() == 2), anyList());
    }

    @Test
    void extractDocument_withUnsupportedFormat_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("files", "menu.csv", "text/csv", "a,b,c".getBytes());

        mockMvc.perform(multipart("/api/v1/menu-import/extract-document")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extractDocument_whenGeminiThrowsProcessingException_shouldReturn422() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        when(geminiService.extractMenuFromDocuments(anyList(), anyList()))
                .thenThrow(new MenuImportProcessingException("Falha ao consultar o serviço de IA."));

        mockMvc.perform(multipart("/api/v1/menu-import/extract-document")
                        .file(validImageFile())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void extractDocument_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(multipart("/api/v1/menu-import/extract-document")
                        .file(validImageFile())
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void commit_withNewCategoryAndProduct_shouldCreateBoth() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        MenuImportProductItem item = new MenuImportProductItem();
        item.setName("Fries");
        item.setPrice(new BigDecimal("12.00"));
        item.setCategoryName("Acompanhamentos");
        MenuImportCommitRequest request = new MenuImportCommitRequest();
        request.setProducts(List.of(item));

        mockMvc.perform(post("/api/v1/menu-import/commit")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriesCreated").value(1))
                .andExpect(jsonPath("$.categoriesReused").value(0))
                .andExpect(jsonPath("$.productsCreated").value(1))
                .andExpect(jsonPath("$.skipped.length()").value(0));

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Acompanhamentos')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void commit_withExistingCategoryNameCaseInsensitive_shouldReuseNotDuplicate() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createCategory(ownerToken, "Bebidas");

        MenuImportProductItem item = new MenuImportProductItem();
        item.setName("Água");
        item.setPrice(new BigDecimal("3.00"));
        item.setCategoryName("bebidas");
        MenuImportCommitRequest request = new MenuImportCommitRequest();
        request.setProducts(List.of(item));

        mockMvc.perform(post("/api/v1/menu-import/commit")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriesCreated").value(0))
                .andExpect(jsonPath("$.categoriesReused").value(1));

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void commit_withOneDuplicateProductAmongMany_shouldSkipOnlyThatRowAndSucceedOthers() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");
        createProduct(ownerToken, categoryId, "Cheeseburger", "25.90");

        MenuImportProductItem duplicate = new MenuImportProductItem();
        duplicate.setName("Cheeseburger");
        duplicate.setPrice(new BigDecimal("29.90"));
        duplicate.setCategoryName("Burgers");

        MenuImportProductItem fresh = new MenuImportProductItem();
        fresh.setName("Fries");
        fresh.setPrice(new BigDecimal("12.00"));
        fresh.setCategoryName("Burgers");

        MenuImportCommitRequest request = new MenuImportCommitRequest();
        request.setProducts(List.of(duplicate, fresh));

        mockMvc.perform(post("/api/v1/menu-import/commit")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productsCreated").value(1))
                .andExpect(jsonPath("$.skipped.length()").value(1))
                .andExpect(jsonPath("$.skipped[0].productName").value("Cheeseburger"));

        mockMvc.perform(get("/api/v1/products").param("search", "Fries")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void commit_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        MenuImportProductItem item = new MenuImportProductItem();
        item.setName("Fries");
        item.setPrice(new BigDecimal("12.00"));
        item.setCategoryName("Acompanhamentos");
        MenuImportCommitRequest request = new MenuImportCommitRequest();
        request.setProducts(List.of(item));

        mockMvc.perform(post("/api/v1/menu-import/commit")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void commit_withMissingPrice_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        MenuImportProductItem item = new MenuImportProductItem();
        item.setName("Fries");
        item.setCategoryName("Acompanhamentos");
        MenuImportCommitRequest request = new MenuImportCommitRequest();
        request.setProducts(List.of(item));

        mockMvc.perform(post("/api/v1/menu-import/commit")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
