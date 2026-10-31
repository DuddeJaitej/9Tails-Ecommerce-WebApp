package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.CustomerReviewDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.dto.ProductRequest;
import com.nintails.ecommerce.dto.ProductOwnerAssignmentRequest;
import com.nintails.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // GET /api/products?page=0&size=20&sort=newest
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAllProducts(page, size, sort)));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getTopRatedProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getTopRatedProducts(limit)));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<CustomerReviewDto>>> getCustomerReviews(
            @RequestParam(defaultValue = "10") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getLatestCustomerReviews(boundedLimit)));
    }

    @GetMapping("/owned")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> getOwnedProducts(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getOwnedProducts(user.getUsername(), page, size)));
    }

    // GET /api/products/category/Fashion
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> getByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getByCategory(category, page, size, sort)));
    }

    // GET /api/products/search?q=watch
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.searchProducts(q, page, size)));
    }

    // GET /api/products/filter?min=100&max=5000
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> filterByPrice(
            @RequestParam(defaultValue = "0") BigDecimal min,
            @RequestParam(defaultValue = "99999") BigDecimal max,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.filterByPrice(min, max, page, size)));
    }

    // GET /api/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    // GET /api/products/{id}/related
    @GetMapping("/{id}/related")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getRelated(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getRelatedProducts(id)));
    }

    // POST /api/products  [OWNER or ADMIN]
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody ProductRequest request) {
        boolean ownerProduct = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created",
                        productService.createProduct(request, user.getUsername(), ownerProduct)));
    }

    // PUT /api/products/{id}  [OWNER or ADMIN]
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        boolean ownerAction = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        return ResponseEntity.ok(ApiResponse.ok("Product updated",
                productService.updateProduct(id, request, user.getUsername(), ownerAction)));
    }

    @PatchMapping("/{id}/owner")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> assignOwner(
            @PathVariable Long id,
            @Valid @RequestBody ProductOwnerAssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product owner assigned",
                productService.assignOwner(id, request.getOwnerId(), request.getBusinessLocationId())));
    }

    // DELETE /api/products/{id}  [OWNER or ADMIN]
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        boolean ownerAction = user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        productService.deleteProduct(id, user.getUsername(), ownerAction);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }
}
