package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeliveryAgentDto {
    Long id;
    String fullName;
    String email;
    Long warehouseId;
    String warehouseName;
    boolean active;
}
