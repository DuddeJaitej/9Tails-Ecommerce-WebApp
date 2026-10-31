package com.nintails.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.nintails.ecommerce.entity.User;

@Data
public class DeliveryRegistrationRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 254, message = "Email must be at most 254 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
    private String password;

    @NotNull(message = "Choose a staff account type")
    private User.Role role;

    private Long deliveryWarehouseId;

    @Size(max = 100)
    private String warehouseName;

    @Size(max = 255)
    private String warehouseStreet;

    @Size(max = 100)
    private String warehouseCity;

    @Size(max = 100)
    private String warehouseState;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid six-digit Indian PIN code")
    private String warehousePostalCode;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double warehouseLatitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double warehouseLongitude;

}
