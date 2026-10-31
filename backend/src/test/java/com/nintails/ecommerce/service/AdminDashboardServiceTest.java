package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AdminDashboardDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.repository.OrderRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AdminDashboardService service;

    @Test
    void orderRowsDoNotPresentOwnerAccountAsCustomer() {
        User owner = User.builder()
                .id(7L)
                .fullName("Akhil")
                .email("owner@example.test")
                .role(User.Role.OWNER)
                .build();
        Order order = Order.builder()
                .id(9L)
                .orderNumber("ORD-TEST")
                .user(owner)
                .addressFullName("Akhil")
                .total(BigDecimal.TEN)
                .status(Order.OrderStatus.DELIVERED)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMethod(Order.PaymentMethod.UPI)
                .placedAt(LocalDateTime.now())
                .build();
        when(orderRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<AdminDashboardDto.OrderSummary> result = service.getOrders(0, 20);

        assertEquals("Customer", result.getContent().get(0).getCustomerName());
        assertNull(result.getContent().get(0).getCustomerEmail());
    }

    @Test
    void orderRowsUseRecipientNameAndCustomerAccountEmail() {
        User customer = User.builder()
                .id(8L)
                .fullName("Customer Account")
                .email("customer@example.test")
                .role(User.Role.USER)
                .build();
        Order order = Order.builder()
                .id(10L)
                .orderNumber("ORD-CUSTOMER")
                .user(customer)
                .addressFullName("Recipient Name")
                .total(BigDecimal.TEN)
                .status(Order.OrderStatus.DELIVERED)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMethod(Order.PaymentMethod.UPI)
                .placedAt(LocalDateTime.now())
                .build();
        when(orderRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<AdminDashboardDto.OrderSummary> result = service.getOrders(0, 20);

        assertEquals("Recipient Name", result.getContent().get(0).getCustomerName());
        assertEquals("customer@example.test", result.getContent().get(0).getCustomerEmail());
    }
}
