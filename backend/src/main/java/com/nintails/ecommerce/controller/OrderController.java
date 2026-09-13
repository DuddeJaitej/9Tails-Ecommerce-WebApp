package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.OrderDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.PlaceOrderRequest;
import com.nintails.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // POST /api/orders  — place order from cart
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> placeOrder(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderDto order = orderService.placeOrder(user.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order placed successfully", order));
    }

    // GET /api/orders
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderDto>>> getMyOrders(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getUserOrders(user.getUsername(), page, size)));
    }

    // GET /api/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrderDetail(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getOrderDetail(user.getUsername(), id)));
    }

    // PATCH /api/orders/{id}/cancel
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled",
                orderService.cancelOrder(user.getUsername(), id)));
    }

    // PATCH /api/orders/{id}/return
    @PatchMapping("/{id}/return")
    public ResponseEntity<ApiResponse<OrderDto>> requestReturn(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Return requested",
                orderService.requestReturn(user.getUsername(), id)));
    }

    // PATCH /api/orders/{id}/exchange
    @PatchMapping("/{id}/exchange")
    public ResponseEntity<ApiResponse<OrderDto>> requestExchange(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Exchange requested",
                orderService.requestExchange(user.getUsername(), id)));
    }

    // PATCH /api/orders/{id}/status  [ADMIN]
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                orderService.updateStatus(id, status)));
    }
}
