package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class NotificationDto {
    Long id;
    String type;
    String title;
    String message;
    Long orderId;
    String orderNumber;
    String actionUrl;
    boolean read;
    LocalDateTime createdAt;
}
