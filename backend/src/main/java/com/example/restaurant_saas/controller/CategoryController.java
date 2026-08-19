package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.UpdateCategoryRequest;
import com.example.restaurant_saas.dto.response.CategoryResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.CategoryService;
import com.example.restaurant_saas.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Manage the restaurant's menu categories. Write operations restricted to OWNER and MANAGER.")
public class CategoryController {

    private final CategoryService categoryService;
    private final FileStorageService fileStorageService;

    @GetMapping
    @Operation(summary = "List categories", description = "Lists the restaurant's categories, optionally filtered by active status.")
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(categoryService.listCategories(currentUser.getRestaurantId(), active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category", description = "Returns a single category by id, scoped to the authenticated user's restaurant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category returned"),
            @ApiResponse(responseCode = "400", description = "Category not found in this restaurant")
    })
    public ResponseEntity<CategoryResponse> getCategory(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(categoryService.getCategory(currentUser.getRestaurantId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Create category", description = "Creates a new category. Name must be unique within the restaurant (case-insensitive).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate name"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse response = categoryService.createCategory(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Update category", description = "Updates name, icon, and/or active status. Fields omitted from the request body are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated"),
            @ApiResponse(responseCode = "400", description = "Category not found, validation error, or duplicate name"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(currentUser.getRestaurantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Delete category", description = "Permanently deletes a category. Only allowed if it has no products; otherwise deactivate it instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted"),
            @ApiResponse(responseCode = "400", description = "Category not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER, or category still has products")
    })
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        categoryService.deleteCategory(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-banner")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Upload category banner image", description = "Uploads a JPEG/PNG/WEBP image (max 5MB) and returns its public URL, to be used as a category's bannerImageUrl.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing file"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<Map<String, String>> uploadBanner(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.storeCategoryBanner(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
