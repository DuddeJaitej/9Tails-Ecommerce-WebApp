package com.nintails.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private boolean inStock;
    private String imageUrl;
    private List<String> galleryImages;
    private Double rating;
    private Integer reviewCount;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
}
