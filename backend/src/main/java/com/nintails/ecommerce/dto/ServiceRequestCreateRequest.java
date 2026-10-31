package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.ServiceRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ServiceRequestCreateRequest {
    @NotNull
    private ServiceRequest.Type type;

    @NotNull
    private ServiceRequest.Reason reason;

    @NotBlank
    @Size(max = 1000)
    private String details;

    @NotBlank
    @Size(max = 500)
    private String photoUrl;

    @NotBlank
    @Size(max = 500)
    private String videoUrl;
}
