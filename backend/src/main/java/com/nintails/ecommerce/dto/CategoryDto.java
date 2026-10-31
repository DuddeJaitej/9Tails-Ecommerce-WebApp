package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.Category;
import lombok.Builder;

@Builder
public record CategoryDto(Long id, String name, String description, String imageUrl) {

    public static CategoryDto from(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .build();
    }
}
