package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.ServiceRequest;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ServiceRequestDto {
    Long id;
    Long orderItemId;
    Long fulfillmentId;
    String orderNumber;
    String productName;
    ServiceRequest.Type type;
    ServiceRequest.Reason reason;
    String details;
    String photoUrl;
    String videoUrl;
    ServiceRequest.Status status;
    String reviewNotes;
    Long warehouseId;
    String warehouseName;
    String warehouseStreet;
    String warehouseCity;
    String warehousePostalCode;
    Double warehouseLatitude;
    Double warehouseLongitude;
    Long deliveryAgentId;
    String deliveryAgentName;
    String customerName;
    String customerStreet;
    String customerCity;
    String customerPostalCode;
    Double customerLatitude;
    Double customerLongitude;
    String sellerName;
    String sellerStreet;
    String sellerCity;
    String sellerPostalCode;
    Double sellerLatitude;
    Double sellerLongitude;
    LocalDateTime requestedAt;
    LocalDateTime pickedUpAt;
    LocalDateTime warehouseReceivedAt;
    LocalDateTime sellerReceivedAt;
    LocalDateTime replacementDeliveredAt;
}
