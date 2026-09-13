package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.dto.ProductRequest;
import com.nintails.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // POST /api/products  [ADMIN]
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.createProduct(request)));
    }

    // PUT /api/products/{id}  [ADMIN]
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated",
                productService.updateProduct(id, request)));
    }

    // DELETE /api/products/{id}  [ADMIN]
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }
}
