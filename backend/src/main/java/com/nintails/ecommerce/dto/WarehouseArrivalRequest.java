package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WarehouseArrivalRequest {
    @NotBlank
    @Size(max = 128)
    private String token;

    @NotNull
    private Long fulfillmentId;

    @Size(max = 1000)
    private String notes;
}
