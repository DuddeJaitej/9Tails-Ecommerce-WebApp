package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PhoneVerificationCodeRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "Enter the six-digit verification code")
    private String code;
}
