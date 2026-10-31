package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminAccountDto {
    Long id;
    String fullName;
    String email;
}
