package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessLocationRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Size(max = 255)
    private String street;

    @NotBlank @Size(max = 100)
    private String city;

    @NotBlank @Size(max = 100)
    private String state;

    @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid six-digit Indian PIN code")
    private String postalCode;

    @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid Indian mobile number")
    private String contactPhone;

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;

    private Boolean verifiedByOwner = true;
}
