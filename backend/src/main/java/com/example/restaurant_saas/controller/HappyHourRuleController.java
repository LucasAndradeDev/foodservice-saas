package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.HappyHourRuleRequest;
import com.example.restaurant_saas.dto.response.HappyHourRuleResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.HappyHourRuleService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/happy-hour-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Happy Hour Rules", description = "Time-window discount rules applied automatically to a whole category. Restricted to OWNER and MANAGER.")
public class HappyHourRuleController {

    private final HappyHourRuleService ruleService;

    @GetMapping
    @Operation(summary = "List happy hour rules", description = "Lists the restaurant's happy hour rules.")
    public ResponseEntity<List<HappyHourRuleResponse>> listRules(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return ResponseEntity.ok(ruleService.listRules(currentUser.getRestaurantId()));
    }

    @PostMapping
    @Operation(summary = "Create happy hour rule", description = "Creates a new happy hour rule for a category.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created"),
            @ApiResponse(responseCode = "400", description = "Validation error or category not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<HappyHourRuleResponse> createRule(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody HappyHourRuleRequest request
    ) {
        HappyHourRuleResponse response = ruleService.createRule(currentUser.getRestaurantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update happy hour rule", description = "Replaces an existing happy hour rule.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated"),
            @ApiResponse(responseCode = "400", description = "Validation error, or rule/category not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<HappyHourRuleResponse> updateRule(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody HappyHourRuleRequest request
    ) {
        return ResponseEntity.ok(ruleService.updateRule(currentUser.getRestaurantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete happy hour rule", description = "Permanently deletes a happy hour rule.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deleted"),
            @ApiResponse(responseCode = "400", description = "Rule not found in this restaurant"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<Void> deleteRule(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID id
    ) {
        ruleService.deleteRule(currentUser.getRestaurantId(), id);
        return ResponseEntity.noContent().build();
    }
}
