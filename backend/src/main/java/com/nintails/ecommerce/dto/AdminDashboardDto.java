package com.nintails.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDto {
    private BigDecimal paidRevenue;
    private long totalOrders;
    private long pendingOrders;
    private long totalCustomers;
    private long activeProducts;
    private long lowStockProducts;
    private List<DailySales> dailySales;
    private List<CountMetric> orderStatuses;
    private List<CountMetric> paymentMethods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySales {
        private LocalDate date;
        private BigDecimal revenue;
        private long orders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountMetric {
        private String label;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private Long id;
        private String orderNumber;
        private String customerName;
        private String customerEmail;
        private BigDecimal total;
        private String status;
        private String paymentStatus;
        private String paymentMethod;
        private LocalDateTime placedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private Long id;
        private String name;
        private String category;
        private BigDecimal price;
        private Integer stock;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSummary {
        private Long id;
        private String name;
        private String email;
        private LocalDateTime joinedAt;
    }
}
