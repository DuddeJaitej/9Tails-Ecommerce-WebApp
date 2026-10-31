package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.ServiceRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServiceRequestStageRequest {
    @NotNull
    private ServiceRequest.Status stage;
}
