package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehousePickupDecisionRequest {
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
