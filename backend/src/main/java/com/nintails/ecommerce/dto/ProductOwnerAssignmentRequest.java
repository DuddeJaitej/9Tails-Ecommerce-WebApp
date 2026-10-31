package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductOwnerAssignmentRequest {
    @NotNull
    private Long ownerId;

    @NotNull
    private Long businessLocationId;
}
