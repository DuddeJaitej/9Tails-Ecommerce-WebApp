package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.AdminDashboardDto;
import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getDashboard()));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<AdminDashboardDto.OrderSummary>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getOrders(page, size)));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<PageResponse<AdminDashboardDto.ProductSummary>>> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getInventory(page, size)));
    }

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<PageResponse<AdminDashboardDto.CustomerSummary>>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getCustomers(page, size)));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be zero or greater and size must be between 1 and 100");
        }
    }
}
