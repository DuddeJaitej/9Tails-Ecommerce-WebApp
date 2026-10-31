package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImageUploadDto {
    String url;
    String fileName;
    long sizeBytes;
}
