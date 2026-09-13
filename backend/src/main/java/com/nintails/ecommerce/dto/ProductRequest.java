package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be positive")
    private BigDecimal price;

    private BigDecimal originalPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock = 0;

    private String imageUrl;

    private String galleryImages;

    @DecimalMin("0.0") @DecimalMax("5.0")
    private Double rating = 0.0;

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}
