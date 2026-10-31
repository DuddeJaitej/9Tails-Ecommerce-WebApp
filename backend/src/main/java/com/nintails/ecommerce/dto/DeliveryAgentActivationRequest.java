package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryAgentActivationRequest {
    @NotNull
    private Boolean active;
}
