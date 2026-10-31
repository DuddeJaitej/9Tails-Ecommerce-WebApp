package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fulfillment_id", nullable = false)
    private SellerFulfillment fulfillment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(length = 40)
    private String previousStatus;

    @Column(length = 40)
    private String newStatus;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 100)
    private String packageCondition;

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
