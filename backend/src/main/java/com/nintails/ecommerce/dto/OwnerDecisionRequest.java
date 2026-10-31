package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OwnerDecisionRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
