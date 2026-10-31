package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seller_fulfillments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_fulfillment_order_seller", columnNames = {"order_id", "seller_key"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellerFulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_location_id")
    private BusinessLocation pickupLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_location_id")
    private WarehouseLocation warehouseLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_store_id")
    private BusinessLocation destinationStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_location_id")
    private WarehouseLocation destinationWarehouseLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "seller_key", nullable = false, length = 320)
    private String sellerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private FulfillmentStatus status = FulfillmentStatus.AWAITING_OWNER_CONFIRMATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_agent_id")
    private User deliveryAgent;

    private LocalDateTime deliveryAgentOfferExpiresAt;
    private LocalDateTime deliveryAgentAcceptedAt;
    private LocalDateTime deliveryAgentRouteDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_delivery_agent_id")
    private User transferDeliveryAgent;

    private LocalDateTime transferAgentOfferExpiresAt;
    private LocalDateTime transferAgentAcceptedAt;
    private LocalDateTime transferAgentRouteDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_delivery_agent_id")
    private User finalDeliveryAgent;

    private LocalDateTime finalAgentOfferExpiresAt;
    private LocalDateTime finalAgentAcceptedAt;
    private LocalDateTime finalAgentRouteDeadline;

    private LocalDateTime expectedDeliveryDate;
    private LocalDateTime ownerRespondedAt;
    private LocalDateTime pickupAt;
    private LocalDateTime warehouseReceivedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmationRequestedAt;

    @Column(length = 40)
    private String customerDeliveryResponse;

    private LocalDateTime customerDeliveryResponseAt;

    @Column(length = 500)
    private String rejectionReason;

    @Column(length = 255)
    private String pickupName;

    @Column(length = 255)
    private String pickupStreet;

    @Column(length = 100)
    private String pickupCity;

    @Column(length = 100)
    private String pickupState;

    @Column(length = 6)
    private String pickupPostalCode;

    @Column(length = 100)
    private String pickupCountry;

    @OneToMany(mappedBy = "fulfillment")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "fulfillment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryStageToken> stageTokens = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum FulfillmentStatus {
        AWAITING_OWNER_CONFIRMATION,
        CONFIRMED,
        REJECTED,
        PICKED_UP_FROM_OWNER,
        PACKED_READY,
        IN_TRANSIT_TO_STORE,
        AT_DESTINATION_STORE,
        OUT_FOR_DELIVERY,
        DELIVERY_CONFIRMATION_PENDING,
        DELIVERED,
        CANCELLED
    }
}
