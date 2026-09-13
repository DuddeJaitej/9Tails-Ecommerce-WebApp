package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.dto.ProductRequest;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.CategoryRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // all reads are transactional by default → fixes LazyInitializationException
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public PageResponse<ProductDto> getAllProducts(int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        return PageResponse.from(productRepository.findByActiveTrue(pageable).map(this::toDto));
    }

    public PageResponse<ProductDto> getByCategory(String category, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        return PageResponse.from(
                productRepository.findByCategoryNameIgnoreCaseAndActiveTrue(category, pageable)
                        .map(this::toDto));
    }

    public PageResponse<ProductDto> searchProducts(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(productRepository.searchProducts(q, pageable).map(this::toDto));
    }

    public PageResponse<ProductDto> filterByPrice(BigDecimal min, BigDecimal max, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(productRepository.findByPriceRange(min, max, pageable).map(this::toDto));
    }

    public ProductDto getProductById(Long id) {
        return toDto(findActiveProduct(id));
    }

    public List<ProductDto> getRelatedProducts(Long productId) {
        Product product = findActiveProduct(productId);
        return productRepository
                .findTop8ByCategoryNameIgnoreCaseAndActiveTrueAndIdNot(
                        product.getCategory().getName(), productId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .stock(request.getStock())
                .imageUrl(request.getImageUrl())
                .galleryImages(request.getGalleryImages())
                .rating(request.getRating())
                .category(category)
                .active(true)
                .build();

        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = findActiveProduct(id);
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setImageUrl(request.getImageUrl());
        product.setGalleryImages(request.getGalleryImages());
        product.setRating(request.getRating());
        product.setCategory(category);

        return toDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findActiveProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    private Product findActiveProduct(Long id) {
        return productRepository.findById(id)
                .filter(Product::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        Sort s = switch (sort == null ? "newest" : sort) {
            case "price-low"  -> Sort.by("price").ascending();
            case "price-high" -> Sort.by("price").descending();
            case "rating"     -> Sort.by("rating").descending();
            default           -> Sort.by("createdAt").descending();
        };
        return PageRequest.of(page, size, s);
    }

    public ProductDto toDto(Product p) {
        List<String> gallery = (p.getGalleryImages() != null && !p.getGalleryImages().isBlank())
                ? Arrays.asList(p.getGalleryImages().split(","))
                : List.of();

        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .stock(p.getStock())
                .inStock(p.getStock() > 0)
                .imageUrl(p.getImageUrl())
                .galleryImages(gallery)
                .rating(p.getRating())
                .reviewCount(p.getReviewCount())
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
