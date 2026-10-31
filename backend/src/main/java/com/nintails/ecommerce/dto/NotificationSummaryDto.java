package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class NotificationSummaryDto {
    List<NotificationDto> notifications;
    long unreadCount;
}
