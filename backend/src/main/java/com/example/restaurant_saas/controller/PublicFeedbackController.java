package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreatePostMealFeedbackRequest;
import com.example.restaurant_saas.dto.response.PublicFeedbackContextResponse;
import com.example.restaurant_saas.service.PublicFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/menu/{slug}/tabs/{tabId}/feedback")
@RequiredArgsConstructor
@Tag(name = "Public Menu", description = "Public, unauthenticated digital menu lookup by restaurant slug.")
public class PublicFeedbackController {

    private final PublicFeedbackService publicFeedbackService;

    @GetMapping
    @Operation(summary = "Get post-meal feedback context", description = "Returns restaurant branding, the table(s) for this tab (empty means Balcão), and whether feedback was already submitted for it. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Context returned"),
            @ApiResponse(responseCode = "400", description = "Restaurant/tab not found, or tab is not closed yet")
    })
    public ResponseEntity<PublicFeedbackContextResponse> getContext(
            @PathVariable String slug,
            @PathVariable UUID tabId
    ) {
        return ResponseEntity.ok(publicFeedbackService.getContext(slug, tabId));
    }

    @PostMapping
    @Operation(summary = "Submit post-meal feedback", description = "Creates a rating (1-5) and optional comment for this closed tab. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback submitted"),
            @ApiResponse(responseCode = "400", description = "Validation error, or restaurant/tab not found, or tab is not closed yet"),
            @ApiResponse(responseCode = "403", description = "Feedback was already submitted for this tab")
    })
    public ResponseEntity<PublicFeedbackContextResponse> submitFeedback(
            @PathVariable String slug,
            @PathVariable UUID tabId,
            @Valid @RequestBody CreatePostMealFeedbackRequest request
    ) {
        return ResponseEntity.ok(publicFeedbackService.submitFeedback(slug, tabId, request));
    }
}
