package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OwnerPickupLocationRequest {

    @NotNull
    private Long businessLocationId;
}
