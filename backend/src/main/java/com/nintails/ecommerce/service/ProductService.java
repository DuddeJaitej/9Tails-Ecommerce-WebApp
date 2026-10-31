package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.CustomerReviewDto;
import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.dto.ProductRatingSummary;
import com.nintails.ecommerce.dto.ProductRequest;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.entity.BusinessLocation;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.repository.BusinessLocationRepository;
import com.nintails.ecommerce.repository.CategoryRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.OrderItemRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // all reads are transactional by default → fixes LazyInitializationException
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessLocationRepository businessLocationRepository;
    private final UserRepository userRepository;

    public PageResponse<ProductDto> getAllProducts(int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        return toProductPage(productRepository.findByActiveTrue(pageable));
    }

    public PageResponse<ProductDto> getByCategory(String category, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        return toProductPage(
                productRepository.findByCategoryNameIgnoreCaseAndActiveTrue(category, pageable));
    }

    public PageResponse<ProductDto> searchProducts(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return toProductPage(productRepository.searchProducts(q, pageable));
    }

    public PageResponse<ProductDto> filterByPrice(BigDecimal min, BigDecimal max, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return toProductPage(productRepository.findByPriceRange(min, max, pageable));
    }

    public ProductDto getProductById(Long id) {
        return toDto(findActiveProduct(id));
    }

    public List<ProductDto> getTopRatedProducts(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        return orderItemRepository.findTopRatedProducts(PageRequest.of(0, boundedLimit)).stream()
                .map(summary -> {
                    Product product = productRepository.findById(summary.productId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Product", summary.productId()));
                    return toDto(product, summary);
                })
                .toList();
    }

    public List<CustomerReviewDto> getLatestCustomerReviews(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        return orderItemRepository.findLatestCustomerReviews(PageRequest.of(0, boundedLimit));
    }

    public List<ProductDto> getRelatedProducts(Long productId) {
        Product product = findActiveProduct(productId);
        return toProductDtos(productRepository
                .findTop8ByCategoryNameIgnoreCaseAndActiveTrueAndIdNot(
                        product.getCategory().getName(), productId));
    }

    public PageResponse<ProductDto> getOwnedProducts(String email, int page, int size) {
        User owner = getOwner(email);
        return toProductPage(productRepository.findByOwnerIdAndActiveTrue(
                owner.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        return createProduct(request, null, false);
    }

    @Transactional
    public ProductDto createProduct(ProductRequest request, String actorEmail, boolean ownerProduct) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        User owner = ownerProduct ? getOwner(actorEmail) : null;
        BusinessLocation location = resolveLocation(request.getBusinessLocationId(), owner);
        if (ownerProduct && location == null) {
            throw new BadRequestException("Select a saved business pickup location.");
        }
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .specifications(request.getSpecifications())
                .productCondition(request.getProductCondition())
                .returnPolicy(request.getReturnPolicy())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .stock(request.getStock())
                .imageUrl(request.getImageUrl())
                .galleryImages(request.getGalleryImages())
                .rating(ownerProduct ? 0.0 : request.getRating())
                .fastDelivery(request.getFastDelivery())
                .category(category)
                .owner(owner)
                .businessLocation(location)
                .active(true)
                .build();

        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        return updateProduct(id, request, null, false);
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request, String actorEmail, boolean ownerAction) {
        Product product = findActiveProduct(id);
        if (ownerAction && (product.getOwner() == null
                || !product.getOwner().getEmail().equalsIgnoreCase(actorEmail))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You cannot manage another owner's product.");
        }
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        BusinessLocation location = resolveLocation(request.getBusinessLocationId(), product.getOwner());
        if (product.getOwner() != null && location == null) {
            throw new BadRequestException("Select a saved business pickup location.");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setShortDescription(request.getShortDescription());
        product.setSpecifications(request.getSpecifications());
        product.setProductCondition(request.getProductCondition());
        product.setReturnPolicy(request.getReturnPolicy());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setImageUrl(request.getImageUrl());
        product.setGalleryImages(request.getGalleryImages());
        if (product.getOwner() == null) {
            product.setRating(request.getRating());
        }
        product.setFastDelivery(request.getFastDelivery());
        product.setCategory(category);
        product.setBusinessLocation(location);

        return toDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findActiveProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id, String actorEmail, boolean ownerAction) {
        Product product = findActiveProduct(id);
        if (ownerAction && (product.getOwner() == null
                || !product.getOwner().getEmail().equalsIgnoreCase(actorEmail))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You cannot manage another owner's product.");
        }
        product.setActive(false);
    }

    @Transactional
    public ProductDto assignOwner(Long productId, Long ownerId, Long businessLocationId) {
        Product product = findActiveProduct(productId);
        User owner = userRepository.findByIdAndRole(ownerId, User.Role.OWNER)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", ownerId));
        BusinessLocation location = businessLocationRepository
                .findByIdAndOwnerId(businessLocationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Business location", businessLocationId));
        if (!Boolean.TRUE.equals(location.getVerifiedByOwner())) {
            throw new BadRequestException("Only a confirmed pickup location can be assigned to a product.");
        }
        product.setOwner(owner);
        product.setBusinessLocation(location);
        return toDto(productRepository.save(product));
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
        return toDto(p, orderItemRepository.findRatingSummaryByProductId(p.getId()).orElse(null));
    }

    private PageResponse<ProductDto> toProductPage(Page<Product> products) {
        Map<Long, ProductRatingSummary> summaries = loadRatingSummaries(products.getContent());
        return PageResponse.from(products.map(product ->
                toDto(product, summaries.get(product.getId()))));
    }

    private List<ProductDto> toProductDtos(List<Product> products) {
        Map<Long, ProductRatingSummary> summaries = loadRatingSummaries(products);
        return products.stream()
                .map(product -> toDto(product, summaries.get(product.getId())))
                .toList();
    }

    private Map<Long, ProductRatingSummary> loadRatingSummaries(List<Product> products) {
        if (products.isEmpty()) return Map.of();
        List<Long> productIds = products.stream().map(Product::getId).toList();
        return orderItemRepository.findRatingSummariesByProductIds(productIds).stream()
                .collect(Collectors.toMap(ProductRatingSummary::productId, summary -> summary));
    }

    private ProductDto toDto(Product p, ProductRatingSummary ratingSummary) {
        List<String> gallery = (p.getGalleryImages() != null && !p.getGalleryImages().isBlank())
                ? Arrays.asList(p.getGalleryImages().split(","))
                : List.of();

        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .shortDescription(p.getShortDescription())
                .specifications(p.getSpecifications())
                .productCondition(p.getProductCondition())
                .returnPolicy(p.getReturnPolicy())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .stock(p.getStock())
                .inStock(p.getStock() > 0)
                .imageUrl(p.getImageUrl())
                .galleryImages(gallery)
                .rating(ratingSummary == null ? 0.0 : ratingSummary.rating())
                .reviewCount(ratingSummary == null ? 0 : ratingSummary.reviewCount().intValue())
                .fastDelivery(Boolean.TRUE.equals(p.getFastDelivery()))
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .businessLocationId(p.getBusinessLocation() == null ? null : p.getBusinessLocation().getId())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private User getOwner(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.Role.OWNER) {
            throw new org.springframework.security.access.AccessDeniedException("An owner account is required.");
        }
        return user;
    }

    private BusinessLocation resolveLocation(Long locationId, User owner) {
        if (locationId == null) {
            return null;
        }
        if (owner == null) {
            return businessLocationRepository.findById(locationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Business location", locationId));
        }
        return businessLocationRepository.findByIdAndOwnerId(locationId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Business location", locationId));
    }
}
