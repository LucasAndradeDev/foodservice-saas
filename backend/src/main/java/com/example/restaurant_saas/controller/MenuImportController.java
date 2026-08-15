package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.MenuImportCommitRequest;
import com.example.restaurant_saas.dto.response.MenuImportCommitResponse;
import com.example.restaurant_saas.dto.response.MenuImportPreviewResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.MenuImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu-import")
@RequiredArgsConstructor
@Tag(name = "Menu Import", description = "AI-assisted import of menu categories and products from an uploaded Excel spreadsheet or PDF/photos of a menu. Extraction never writes to the database; only commit persists data.")
public class MenuImportController {

    private final MenuImportService menuImportService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Extract menu from spreadsheet", description = "Parses an uploaded .xlsx and uses AI to structure it into categories/products for review. Never persists anything.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extraction succeeded"),
            @ApiResponse(responseCode = "400", description = "Missing file or not a .xlsx"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER"),
            @ApiResponse(responseCode = "422", description = "File unreadable or AI extraction failed")
    })
    public ResponseEntity<MenuImportPreviewResponse> extract(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(menuImportService.extract(currentUser.getRestaurantId(), file));
    }

    @PostMapping(value = "/extract-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Extract menu from PDF or photos", description = "Reads one or more uploaded PDF/image files (e.g. photos of a physical menu) and uses AI to structure them into categories/products for review. Never persists anything.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extraction succeeded"),
            @ApiResponse(responseCode = "400", description = "Missing files, too many files, or unsupported format"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER"),
            @ApiResponse(responseCode = "422", description = "Files unreadable or AI extraction failed")
    })
    public ResponseEntity<MenuImportPreviewResponse> extractDocument(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(menuImportService.extractFromDocuments(currentUser.getRestaurantId(), files));
    }

    @PostMapping("/commit")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Operation(summary = "Commit reviewed menu import", description = "Persists reviewed categories/products. Categories are matched case-insensitively and reused; duplicate product names are skipped and reported, not overwritten.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commit processed (see body for per-item results)"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<MenuImportCommitResponse> commit(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody MenuImportCommitRequest request
    ) {
        return ResponseEntity.ok(menuImportService.commit(currentUser.getRestaurantId(), request));
    }
}
