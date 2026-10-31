package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AdminDashboardDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.repository.OrderRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public AdminDashboardDto getDashboard() {
        List<Order> weeklyOrders = orderRepository.findAllByPlacedAtAfter(
                LocalDate.now().minusDays(6).atStartOfDay());
        List<Order> weeklyPaidOrders = orderRepository.findAllByPlacedAtAfterAndPaymentStatus(
                LocalDate.now().minusDays(6).atStartOfDay(), Order.PaymentStatus.PAID);

        Map<LocalDate, BigDecimal> dailyRevenue = new java.util.HashMap<>();
        Map<LocalDate, Long> dailyOrderCounts = new java.util.HashMap<>();
        weeklyOrders.stream()
                .filter(order -> order.getPlacedAt() != null)
                .forEach(order -> dailyOrderCounts.merge(order.getPlacedAt().toLocalDate(), 1L, Long::sum));
        weeklyPaidOrders.stream()
                .filter(order -> order.getPlacedAt() != null)
                .forEach(order -> dailyRevenue.merge(order.getPlacedAt().toLocalDate(),
                        order.getTotal(), BigDecimal::add));

        LocalDate firstDay = LocalDate.now().minusDays(6);
        List<AdminDashboardDto.DailySales> dailySales = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = firstDay.plusDays(day);
            dailySales.add(AdminDashboardDto.DailySales.builder()
                    .date(date)
                    .revenue(dailyRevenue.getOrDefault(date, BigDecimal.ZERO))
                    .orders(dailyOrderCounts.getOrDefault(date, 0L))
                    .build());
        }

        List<AdminDashboardDto.CountMetric> orderStatuses = new ArrayList<>();
        for (Order.OrderStatus status : Order.OrderStatus.values()) {
            orderStatuses.add(AdminDashboardDto.CountMetric.builder()
                    .label(status.name())
                    .count(orderRepository.countByStatus(status))
                    .build());
        }

        List<AdminDashboardDto.CountMetric> paymentMethods = new ArrayList<>();
        for (Order.PaymentMethod method : Order.PaymentMethod.values()) {
            paymentMethods.add(AdminDashboardDto.CountMetric.builder()
                    .label(method.name())
                    .count(orderRepository.countByPaymentMethod(method))
                    .build());
        }

        return AdminDashboardDto.builder()
                .paidRevenue(orderRepository.sumTotalByPaymentStatus(Order.PaymentStatus.PAID))
                .totalOrders(orderRepository.count())
                .pendingOrders(orderRepository.countByStatus(Order.OrderStatus.PENDING))
                .totalCustomers(userRepository.countByRole(User.Role.USER))
                .activeProducts(productRepository.countByActiveTrue())
                .lowStockProducts(productRepository.countByActiveTrueAndStockLessThanEqual(5))
                .dailySales(dailySales)
                .orderStatuses(orderStatuses)
                .paymentMethods(paymentMethods)
                .build();
    }

    public PageResponse<AdminDashboardDto.OrderSummary> getOrders(int page, int size) {
        return PageResponse.from(orderRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "placedAt")))
                .map(order -> AdminDashboardDto.OrderSummary.builder()
                        .id(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .customerName(customerDisplayName(order))
                        .customerEmail(customerEmail(order))
                        .total(order.getTotal())
                        .status(order.getStatus().name())
                        .paymentStatus(order.getPaymentStatus().name())
                        .paymentMethod(order.getPaymentMethod() == null ? "UNKNOWN" :
                                order.getPaymentMethod().name())
                        .placedAt(order.getPlacedAt())
                        .build()));
    }

    private String customerDisplayName(Order order) {
        User purchaser = order.getUser();
        String recipientName = order.getAddressFullName();
        if (recipientName != null && !recipientName.isBlank()
                        && (purchaser.getRole() == User.Role.USER
                        || !recipientName.equalsIgnoreCase(purchaser.getFullName()))) {
            return recipientName;
        }
        return purchaser.getRole() == User.Role.USER ? purchaser.getFullName() : "Customer";
    }

    private String customerEmail(Order order) {
        User purchaser = order.getUser();
        return purchaser.getRole() == User.Role.USER ? purchaser.getEmail() : null;
    }

    public PageResponse<AdminDashboardDto.ProductSummary> getInventory(int page, int size) {
        return PageResponse.from(productRepository.findByActiveTrue(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")))
                .map(this::toProductSummary));
    }

    public PageResponse<AdminDashboardDto.CustomerSummary> getCustomers(int page, int size) {
        return PageResponse.from(userRepository.findByRole(User.Role.USER,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(user -> AdminDashboardDto.CustomerSummary.builder()
                        .id(user.getId())
                        .name(user.getFullName())
                        .email(user.getEmail())
                        .joinedAt(user.getCreatedAt())
                        .build()));
    }

    private AdminDashboardDto.ProductSummary toProductSummary(Product product) {
        return AdminDashboardDto.ProductSummary.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory().getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .build();
    }
}
