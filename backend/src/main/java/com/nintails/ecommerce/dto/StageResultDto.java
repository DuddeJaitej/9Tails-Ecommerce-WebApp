package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StageResultDto {
    FulfillmentDto fulfillment;
    String nextStage;
    String nextQrToken;
}
