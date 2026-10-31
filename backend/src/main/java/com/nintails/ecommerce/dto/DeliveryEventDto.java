package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DeliveryEventDto {
    Long id;
    Long fulfillmentId;
    String orderNumber;
    String eventType;
    String previousStatus;
    String newStatus;
    String outcome;
    String packageCondition;
    String notes;
    String actorName;
    LocalDateTime createdAt;
}
