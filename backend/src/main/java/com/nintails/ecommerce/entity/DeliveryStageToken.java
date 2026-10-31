package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_stage_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_delivery_token_hash", columnNames = "token_hash")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryStageToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fulfillment_id", nullable = false)
    private SellerFulfillment fulfillment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Stage stage;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_ciphertext", nullable = false, length = 256)
    private String tokenCiphertext;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime issuedAt;

    public enum Stage {
        PICKUP_FROM_OWNER,
        WAREHOUSE_RECEIVED,
        OUT_FOR_DELIVERY_TO_STORE,
        STORE_RECEIVED,
        OUT_FOR_DELIVERY,
        DELIVERED
    }
}
