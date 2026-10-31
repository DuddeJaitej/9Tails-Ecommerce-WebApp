package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeliveryScanResultDto {
    String stage;
    FulfillmentDto fulfillment;
}
