package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.SetMonthlyGoalRequest;
import com.example.restaurant_saas.dto.response.FeedbackPageResponse;
import com.example.restaurant_saas.dto.response.FeedbackReportResponse;
import com.example.restaurant_saas.dto.response.MonthlyGoalResponse;
import com.example.restaurant_saas.dto.response.PeakHoursResponse;
import com.example.restaurant_saas.dto.response.ReportSummaryResponse;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.GoalService;
import com.example.restaurant_saas.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
@Tag(name = "Reports", description = "Financial reports over closed tabs. Restricted to OWNER and MANAGER.")
public class ReportController {

    private final ReportService reportService;
    private final GoalService goalService;

    @GetMapping("/summary")
    @Operation(summary = "Get financial summary", description = "Returns total revenue, closed tabs count, average ticket, revenue by payment method and top selling products for tabs paid within the given date range (inclusive).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary returned"),
            @ApiResponse(responseCode = "400", description = "start is after end"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<ReportSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(reportService.getSummary(currentUser.getRestaurantId(), start, end));
    }

    @GetMapping("/peak-hours")
    @Operation(summary = "Get peak hours grid", description = "Returns, for each day of week and hour of day, the average number of occupied tables and the average number of orders created, within the given date range (inclusive). Averages are computed over how many times each weekday occurs in the range. Only tabs with at least one table are considered (counter/Balcão tabs are excluded).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grid returned"),
            @ApiResponse(responseCode = "400", description = "start is after end"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<PeakHoursResponse> getPeakHours(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(reportService.getPeakHours(currentUser.getRestaurantId(), start, end));
    }

    @GetMapping("/feedback")
    @Operation(summary = "Get post-meal feedback report", description = "Returns the average rating, total count, and the most recent feedback entries (capped) submitted by customers for tabs closed within the given date range (inclusive).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report returned"),
            @ApiResponse(responseCode = "400", description = "start is after end"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<FeedbackReportResponse> getFeedbackReport(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(reportService.getFeedbackReport(currentUser.getRestaurantId(), start, end));
    }

    @GetMapping("/feedback/entries")
    @Operation(summary = "Get paginated post-meal feedback entries", description = "Returns a page of feedback entries submitted by customers for tabs closed within the given date range (inclusive), most recent first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page returned"),
            @ApiResponse(responseCode = "400", description = "start is after end"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<FeedbackPageResponse> getFeedbackEntries(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reportService.getFeedbackEntries(currentUser.getRestaurantId(), start, end, page, size));
    }

    @GetMapping("/goals")
    @Operation(summary = "Get monthly goal", description = "Returns the revenue goal set for the given month (any day within the month), the actual revenue so far, and the progress percentage. revenueGoal and progressPercentage are null when no goal was set for that month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal returned"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<MonthlyGoalResponse> getMonthlyGoal(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month
    ) {
        return ResponseEntity.ok(goalService.getMonthlyGoal(currentUser.getRestaurantId(), month));
    }

    @PutMapping("/goals")
    @Operation(summary = "Set monthly goal", description = "Creates or updates the revenue goal for the given month (any day within the month is normalized to the 1st).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal saved"),
            @ApiResponse(responseCode = "400", description = "Revenue goal is missing or not greater than zero"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not OWNER or MANAGER")
    })
    public ResponseEntity<MonthlyGoalResponse> setMonthlyGoal(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody SetMonthlyGoalRequest request
    ) {
        return ResponseEntity.ok(goalService.setMonthlyGoal(currentUser.getRestaurantId(), request));
    }
}
