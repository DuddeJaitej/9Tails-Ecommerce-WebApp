package com.nintails.ecommerce.dto;

public record CustomerReviewDto(
        Long id,
        String reviewText,
        Integer rating,
        String customerName,
        String profilePhotoUrl) {
}
