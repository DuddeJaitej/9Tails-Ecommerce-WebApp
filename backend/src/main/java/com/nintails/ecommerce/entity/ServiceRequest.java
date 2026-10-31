package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Reason reason;

    @Column(nullable = false, length = 1000)
    private String details;

    @Column(nullable = false, length = 500)
    private String photoUrl;

    @Column(nullable = false, length = 500)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private Status status = Status.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(length = 1000)
    private String reviewNotes;

    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_location_id")
    private WarehouseLocation warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_agent_id")
    private User deliveryAgent;

    private LocalDateTime pickedUpAt;
    private LocalDateTime warehouseReceivedAt;
    private LocalDateTime sellerReceivedAt;
    private LocalDateTime replacementDeliveredAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime requestedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Type { RETURN, EXCHANGE }

    public enum Reason {
        FIT_ISSUE, QUALITY_NOT_AS_EXPECTED, WRONG_ITEM, DAMAGED, SIZE_OR_COLOR_CHANGE, OTHER
    }

    public enum Status {
        REQUESTED, APPROVED, REJECTED, PICKUP_ASSIGNED, PICKED_UP,
        AT_WAREHOUSE, SELLER_ROUTE_ASSIGNED, RECEIVED_BY_SELLER,
        REPLACEMENT_READY, REPLACEMENT_ASSIGNED, COMPLETED
    }
}
