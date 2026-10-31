package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.OrderDto;
import com.nintails.ecommerce.dto.OrderItemRatingRequest;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.PlaceOrderRequest;
import com.nintails.ecommerce.dto.RazorpayOrderRequest;
import com.nintails.ecommerce.dto.RazorpayOrderResponse;
import com.nintails.ecommerce.dto.ProductImageUploadDto;
import com.nintails.ecommerce.dto.ServiceRequestCreateRequest;
import com.nintails.ecommerce.dto.VerifyRazorpayPaymentRequest;
import com.nintails.ecommerce.service.OrderEvidenceStorageService;
import com.nintails.ecommerce.service.OrderService;
import com.nintails.ecommerce.service.ServiceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ServiceRequestService serviceRequestService;
    private final OrderEvidenceStorageService evidenceStorageService;

    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductImageUploadDto>> uploadServiceEvidence(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Return/exchange evidence uploaded", evidenceStorageService.store(file)));
    }

    @PostMapping("/{id}/items/{itemId}/service-request")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<com.nintails.ecommerce.dto.ServiceRequestDto>> createServiceRequest(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody ServiceRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Return/exchange request submitted for owner review",
                serviceRequestService.create(user.getUsername(), id, itemId, request)));
    }

    // POST /api/orders  — place order from cart
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> placeOrder(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderDto order = orderService.placeOrder(user.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order placed successfully", order));
    }

    @PostMapping("/razorpay/order")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createRazorpayOrder(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody RazorpayOrderRequest request) {
        RazorpayOrderResponse response = orderService.createRazorpayOrder(
                user.getUsername(), request.getAddressId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Razorpay order created", response));
    }

    @PostMapping("/razorpay/verify")
    public ResponseEntity<ApiResponse<OrderDto>> verifyRazorpayPayment(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        OrderDto order = orderService.verifyRazorpayPayment(
                user.getUsername(),
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());
        return ResponseEntity.ok(ApiResponse.ok("Payment verified and order confirmed", order));
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

    @PutMapping("/{id}/items/{itemId}/rating")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Integer>> rateOrderItem(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody OrderItemRatingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product rating saved",
                orderService.rateOrderItem(
                        user.getUsername(), id, itemId, request.getRating(), request.getReviewText())));
    }

    // PATCH /api/orders/{id}/cancel
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled",
                orderService.cancelOrder(user.getUsername(), id)));
    }

    // PATCH /api/orders/{id}/items/{itemId}/cancel
    @PatchMapping("/{id}/items/{itemId}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrderItem(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.ok("Order item cancelled",
                orderService.cancelOrderItem(user.getUsername(), id, itemId)));
    }

    // PATCH /api/orders/{id}/status  [ADMIN]
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> updateStatus(
            @AuthenticationPrincipal UserDetails administrator,
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                orderService.updateStatus(administrator.getUsername(), id, status)));
    }
}
