package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_id")
    private SellerFulfillment fulfillment;

    // snapshot values at order time
    private String productName;
    private String productImageUrl;
    private String categoryName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private boolean cancelled;

    @Builder.Default
    @Column(nullable = false)
    private boolean stockReserved = false;

    @Min(1)
    @Max(5)
    @Column(name = "customer_rating")
    private Integer customerRating;

    @Column(name = "customer_review", length = 1000)
    private String customerReview;

    private LocalDateTime customerReviewedAt;
}
