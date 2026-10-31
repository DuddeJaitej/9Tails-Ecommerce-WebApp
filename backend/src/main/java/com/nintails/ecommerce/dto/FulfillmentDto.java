package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;

@Value
@With
@Builder
public class FulfillmentDto {
    Long id;
    Long orderId;
    String orderNumber;
    String status;
    String warehousePickupDecision;
    String warehousePickupDecisionNotes;
    String warehousePickupDecisionBy;
    LocalDateTime warehousePickupDecisionAt;
    String ownerName;
    Long deliveryAgentId;
    String deliveryAgentName;
    LocalDateTime deliveryAgentOfferExpiresAt;
    LocalDateTime deliveryAgentAcceptedAt;
    LocalDateTime deliveryAgentRouteDeadline;
    Long transferDeliveryAgentId;
    String transferDeliveryAgentName;
    LocalDateTime transferAgentOfferExpiresAt;
    LocalDateTime transferAgentAcceptedAt;
    LocalDateTime transferAgentRouteDeadline;
    Long finalDeliveryAgentId;
    String finalDeliveryAgentName;
    LocalDateTime finalAgentOfferExpiresAt;
    LocalDateTime finalAgentAcceptedAt;
    LocalDateTime finalAgentRouteDeadline;
    Long warehouseLocationId;
    Long destinationWarehouseLocationId;
    Long destinationStoreId;
    String warehouseName;
    String warehouseStreet;
    String warehouseCity;
    String warehousePostalCode;
    Double warehouseLatitude;
    Double warehouseLongitude;
    String destinationWarehouseName;
    String destinationWarehouseStreet;
    String destinationWarehouseCity;
    String destinationWarehousePostalCode;
    Double destinationWarehouseLatitude;
    Double destinationWarehouseLongitude;
    String destinationStoreName;
    String destinationStoreStreet;
    String destinationStoreCity;
    String destinationStorePostalCode;
    Double destinationStoreLatitude;
    Double destinationStoreLongitude;
    Double pickupLatitude;
    Double pickupLongitude;
    Double customerLatitude;
    Double customerLongitude;
    String customerName;
    String customerPhone;
    String customerStreet;
    String customerCity;
    String customerPostalCode;
    List<ServiceRequestDto> serviceRequests;
    String pickupName;
    String pickupStreet;
    String pickupCity;
    String pickupState;
    String pickupPostalCode;
    LocalDateTime placedAt;
    LocalDateTime expectedDeliveryDate;
    LocalDateTime ownerRespondedAt;
    LocalDateTime pickupAt;
    LocalDateTime warehouseReceivedAt;
    LocalDateTime dispatchedAt;
    LocalDateTime confirmationRequestedAt;
    LocalDateTime deliveredAt;
    String rejectionReason;
    List<Item> items;

    @Value
    @Builder
    public static class Item {
        String productName;
        String imageUrl;
        Integer quantity;
    }
}
