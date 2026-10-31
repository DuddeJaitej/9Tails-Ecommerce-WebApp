package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PhoneVerificationChallengeDto {
    Long challengeId;
    String maskedPhone;
    LocalDateTime expiresAt;
}
