package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.MenuImportCommitRequest;
import com.example.restaurant_saas.dto.request.MenuImportProductItem;
import com.example.restaurant_saas.dto.response.ExtractedCategoryDto;
import com.example.restaurant_saas.dto.response.ExtractedProductDto;
import com.example.restaurant_saas.dto.response.MenuImportCommitError;
import com.example.restaurant_saas.dto.response.MenuImportCommitResponse;
import com.example.restaurant_saas.dto.response.MenuImportPreviewResponse;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Orchestrates spreadsheet extraction + AI structuring + duplicate matching (extract),
 * and the actual database writes (commit). Extraction never persists anything.
 */
@Service
@RequiredArgsConstructor
public class MenuImportService {

    private static final Pattern NON_NUMERIC_CHARS = Pattern.compile("[^0-9.,-]");
    private static final String EXTRACT_ACTION = "menu-import-extract";

    private final SpreadsheetExtractionService spreadsheetExtractionService;
    private final DocumentExtractionService documentExtractionService;
    private final GeminiService geminiService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpRequest;

    @Value("${security.menu-import-rate-limit.max-attempts}")
    private int extractMaxAttempts;

    @Value("${security.menu-import-rate-limit.window-minutes}")
    private long extractWindowMinutes;

    @Value("${security.menu-import-rate-limit.block-minutes}")
    private long extractBlockMinutes;

    // Not @Transactional: the Gemini call can take up to ~45s and must not hold a DB
    // connection/transaction open. Each repository lookup below gets its own short-lived one.
    public MenuImportPreviewResponse extract(UUID restaurantId, MultipartFile file) {
        // Keyed by restaurantId, not IP: the Gemini API key is shared across every tenant on a
        // free-tier daily quota, so what matters is capping how much of that shared quota a
        // single restaurant can burn through, regardless of which IP it calls from.
        checkExtractionAllowed(restaurantId);
        String flattenedText = spreadsheetExtractionService.extractFlattenedText(file);
        List<String> existingCategoryNames = loadExistingCategoryNames(restaurantId);
        GeminiExtractionResult aiResult = geminiService.extractMenu(flattenedText, existingCategoryNames);
        return buildPreview(restaurantId, aiResult);
    }

    // Not @Transactional, same reasoning as extract() above.
    public MenuImportPreviewResponse extractFromDocuments(UUID restaurantId, List<MultipartFile> files) {
        checkExtractionAllowed(restaurantId);
        List<GeminiDocument> documents = documentExtractionService.extractDocuments(files);
        List<String> existingCategoryNames = loadExistingCategoryNames(restaurantId);
        GeminiExtractionResult aiResult = geminiService.extractMenuFromDocuments(documents, existingCategoryNames);
        return buildPreview(restaurantId, aiResult);
    }

    private void checkExtractionAllowed(UUID restaurantId) {
        String identifier = restaurantId.toString();
        rateLimitService.checkAllowed(EXTRACT_ACTION, httpRequest, identifier);
        rateLimitService.recordAttempt(EXTRACT_ACTION, httpRequest, identifier,
                extractMaxAttempts, extractWindowMinutes, extractBlockMinutes);
    }

    private List<String> loadExistingCategoryNames(UUID restaurantId) {
        return categoryRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(restaurantId).stream()
                .map(Category::getName)
                .toList();
    }

    private MenuImportPreviewResponse buildPreview(UUID restaurantId, GeminiExtractionResult aiResult) {
        // Dedupe category names case-insensitively within the AI's own output first -
        // it might emit "Bebidas" from one sheet/tab and "bebidas" from another.
        Map<String, String> displayNameByKey = new LinkedHashMap<>();
        Map<String, List<GeminiProduct>> productsByKey = new LinkedHashMap<>();
        for (GeminiCategory category : aiResult.categories()) {
            String name = category.name() == null ? "" : category.name().trim();
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase();
            displayNameByKey.putIfAbsent(key, name);
            productsByKey.computeIfAbsent(key, k -> new ArrayList<>())
                    .addAll(category.products() == null ? List.of() : category.products());
        }

        List<ExtractedCategoryDto> categories = new ArrayList<>();
        List<ExtractedProductDto> products = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int categoryIndex = 0;
        int productIndex = 0;
        for (Map.Entry<String, String> entry : displayNameByKey.entrySet()) {
            String key = entry.getKey();
            String displayName = entry.getValue();
            String categoryTempId = "cat-" + categoryIndex++;

            UUID matchedCategoryId = categoryRepository.findByRestaurantIdAndNameIgnoreCase(restaurantId, displayName)
                    .map(Category::getId)
                    .orElse(null);

            categories.add(ExtractedCategoryDto.builder()
                    .tempId(categoryTempId)
                    .name(displayName)
                    .matchedCategoryId(matchedCategoryId)
                    .build());

            for (GeminiProduct product : productsByKey.getOrDefault(key, List.of())) {
                String productName = product.name() == null ? "" : product.name().trim();
                if (productName.isEmpty()) {
                    continue;
                }

                BigDecimal price = normalizePrice(product.price());
                if (price == null) {
                    warnings.add("Preço não identificado para \"" + productName + "\" - preencha manualmente antes de confirmar.");
                }

                UUID duplicateOfProductId = productRepository.findByRestaurantIdAndNameIgnoreCase(restaurantId, productName)
                        .map(Product::getId)
                        .orElse(null);

                products.add(ExtractedProductDto.builder()
                        .tempId("prod-" + productIndex++)
                        .categoryTempId(categoryTempId)
                        .name(productName)
                        .description(product.description())
                        .price(price)
                        .duplicate(duplicateOfProductId != null)
                        .duplicateOfProductId(duplicateOfProductId)
                        .build());
            }
        }

        return MenuImportPreviewResponse.builder()
                .categories(categories)
                .products(products)
                .warnings(warnings)
                .build();
    }

    // Not @Transactional: each row is created via ProductService.createProduct, which
    // has its own @Transactional. A duplicate on one row shouldn't roll back the rest
    // of a 40-row batch, and shouldn't block the rows after it either.
    public MenuImportCommitResponse commit(UUID restaurantId, MenuImportCommitRequest request) {
        int categoriesCreated = 0;
        int categoriesReused = 0;
        int productsCreated = 0;
        List<MenuImportCommitError> skipped = new ArrayList<>();

        for (MenuImportProductItem item : request.getProducts()) {
            boolean categoryExisted = categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, item.getCategoryName());
            Category category = categoryService.findOrCreateCategory(restaurantId, item.getCategoryName());
            if (categoryExisted) {
                categoriesReused++;
            } else {
                categoriesCreated++;
            }

            CreateProductRequest createRequest = new CreateProductRequest();
            createRequest.setName(item.getName());
            createRequest.setDescription(item.getDescription());
            createRequest.setPrice(item.getPrice());
            createRequest.setCategoryId(category.getId());

            try {
                productService.createProduct(restaurantId, createRequest);
                productsCreated++;
            } catch (IllegalArgumentException e) {
                skipped.add(MenuImportCommitError.builder()
                        .productName(item.getName())
                        .reason(e.getMessage())
                        .build());
            }
        }

        return MenuImportCommitResponse.builder()
                .categoriesCreated(categoriesCreated)
                .categoriesReused(categoriesReused)
                .productsCreated(productsCreated)
                .skipped(skipped)
                .build();
    }

    private BigDecimal normalizePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        String cleaned = NON_NUMERIC_CHARS.matcher(rawPrice.trim()).replaceAll("");
        if (cleaned.isEmpty()) {
            return null;
        }

        boolean hasComma = cleaned.contains(",");
        boolean hasDot = cleaned.contains(".");
        if (hasComma && hasDot) {
            // "1.234,56" (BR) - dot is thousands separator, comma is decimal.
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (hasComma) {
            cleaned = cleaned.replace(",", ".");
        }

        try {
            BigDecimal value = new BigDecimal(cleaned);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
