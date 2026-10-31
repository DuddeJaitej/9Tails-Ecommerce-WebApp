package com.nintails.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(unique = true, length = 64)
    private String razorpayOrderId;

    @Column(unique = true, length = 64)
    private String razorpayPaymentId;

    // snapshot of address at order time
    private String addressFullName;
    private String addressStreet;
    private String addressCity;
    private String addressPostalCode;
    private String addressCountry;
    private Double addressLatitude;
    private Double addressLongitude;

    private LocalDateTime expectedDeliveryDate;

    @Builder.Default
    private Boolean returnRequested = false;
    @Builder.Default
    private Boolean exchangeRequested = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SellerFulfillment> fulfillments = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime placedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum OrderStatus {
        PENDING, CONFIRMED, PICKED_UP_FROM_OWNER, PACKED_READY, PACKED, SHIPPED,
        IN_TRANSIT_TO_STORE, AT_DESTINATION_STORE, OUT_FOR_DELIVERY,
        DELIVERY_CONFIRMATION_PENDING, DELIVERED, CANCELLED
    }

    public enum PaymentMethod {
        COD, UPI
    }

    public enum PaymentStatus {
        PENDING, PAID, FAILED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED
    }
}
