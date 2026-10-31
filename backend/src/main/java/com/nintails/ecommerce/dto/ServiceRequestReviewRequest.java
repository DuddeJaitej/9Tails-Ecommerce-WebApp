package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ServiceRequestReviewRequest {
    @NotBlank
    private String decision;

    @Size(max = 1000)
    private String notes;
}
