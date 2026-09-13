package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.CartSummaryDto;
import com.nintails.ecommerce.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartSummaryDto>> getCart(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(user.getUsername())));
    }

    // POST /api/cart?productId=5&quantity=2
    @PostMapping
    public ResponseEntity<ApiResponse<CartSummaryDto>> addItem(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity) {
        return ResponseEntity.ok(ApiResponse.ok("Item added",
                cartService.addItem(user.getUsername(), productId, quantity)));
    }

    // PUT /api/cart/{productId}?quantity=3
    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<CartSummaryDto>> updateQuantity(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(ApiResponse.ok("Cart updated",
                cartService.updateQuantity(user.getUsername(), productId, quantity)));
    }

    // DELETE /api/cart/{productId}
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<CartSummaryDto>> removeItem(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Item removed",
                cartService.removeItem(user.getUsername(), productId)));
    }

    // DELETE /api/cart
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserDetails user) {
        cartService.clearCart(user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Cart cleared", null));
    }
}
