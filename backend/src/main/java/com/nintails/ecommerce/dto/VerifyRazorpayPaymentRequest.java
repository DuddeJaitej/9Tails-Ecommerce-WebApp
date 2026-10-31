package com.nintails.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRazorpayPaymentRequest {

    @NotBlank(message = "Razorpay order ID is required")
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment ID is required")
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay payment signature is required")
    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}
