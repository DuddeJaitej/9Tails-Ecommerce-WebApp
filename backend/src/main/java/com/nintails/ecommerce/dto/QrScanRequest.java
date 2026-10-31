package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QrScanRequest {
    @NotBlank
    @Size(max = 128)
    private String token;
}
