package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StageSubmissionRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 128)
    private String token;

    @Size(max = 100)
    private String packageCondition;

    @Size(max = 1000)
    private String notes;
}
