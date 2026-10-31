package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class OtpDispatchDto {
    Long fulfillmentId;
    String maskedPhone;
    LocalDateTime expiresAt;
    String message;
}
