package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    // GET /api/wishlist
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> getWishlist(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getWishlist(user.getUsername())));
    }

    // POST /api/wishlist/{productId}  — toggle (add if absent, remove if present)
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> toggle(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long productId) {
        String action = wishlistService.toggleWishlist(user.getUsername(), productId);
        String message = "added".equals(action)
                ? "Product added to wishlist"
                : "Product removed from wishlist";
        return ResponseEntity.ok(ApiResponse.ok(message, Map.of("action", action)));
    }

    // GET /api/wishlist/{productId}/status
    @GetMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long productId) {
        boolean wishlisted = wishlistService.isWishlisted(user.getUsername(), productId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("wishlisted", wishlisted)));
    }
}
