package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDeliveryAgentRequest {
    @NotNull
    private Long deliveryAgentId;
}
