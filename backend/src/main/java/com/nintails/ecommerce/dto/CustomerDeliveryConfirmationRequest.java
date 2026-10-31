package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerDeliveryConfirmationRequest {
    @NotNull
    private Boolean delivered;

    private String notes;
}
