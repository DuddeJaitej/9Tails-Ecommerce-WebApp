package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderDto {
    private Long id;
    private String orderNumber;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal total;
    private String paymentMethod;
    private String paymentStatus;
    private String addressFullName;
    private String addressStreet;
    private String addressCity;
    private String addressPostalCode;
    private String addressCountry;
    private LocalDateTime expectedDeliveryDate;
    private boolean returnRequested;
    private boolean exchangeRequested;
    private List<OrderItemDto> items;
    private LocalDateTime placedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private String productName;
        private String productImageUrl;
        private String categoryName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal lineTotal;
    }
}
