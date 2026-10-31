package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ProductRatingSummary;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.repository.BusinessLocationRepository;
import com.nintails.ecommerce.repository.CategoryRepository;
import com.nintails.ecommerce.repository.OrderItemRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceRatingTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BusinessLocationRepository businessLocationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProductService productService;

    @Test
    void productDtoUsesVerifiedOrderItemRatingsInsteadOfCatalogDefaults() {
        Product product = Product.builder()
                .id(3L)
                .name("Pearl pendant")
                .price(new BigDecimal("899.00"))
                .stock(4)
                .rating(4.9)
                .reviewCount(120)
                .category(Category.builder().name("Jewelry").build())
                .build();
        when(orderItemRepository.findRatingSummaryByProductId(3L))
                .thenReturn(Optional.of(new ProductRatingSummary(3L, 4.5, 2L)));

        var productDto = productService.toDto(product);

        assertEquals(4.5, productDto.getRating());
        assertEquals(2, productDto.getReviewCount());
    }

    @Test
    void unratedProductsDoNotReceiveSeededRatings() {
        Product product = Product.builder()
                .id(4L)
                .name("Unrated product")
                .price(new BigDecimal("499.00"))
                .stock(2)
                .rating(4.8)
                .reviewCount(500)
                .category(Category.builder().name("Fashion").build())
                .build();
        when(orderItemRepository.findRatingSummaryByProductId(4L))
                .thenReturn(Optional.empty());

        var productDto = productService.toDto(product);

        assertEquals(0.0, productDto.getRating());
        assertEquals(0, productDto.getReviewCount());
    }
}
