package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PhoneVerificationRequest {
    @NotBlank
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid Indian mobile number")
    private String phoneNumber;
}
