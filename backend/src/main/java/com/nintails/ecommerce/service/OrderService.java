package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.OrderDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.PlaceOrderRequest;
import com.nintails.ecommerce.entity.*;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("10");

    @Transactional
    public OrderDto placeOrder(String email, PlaceOrderRequest request) {
        User user = getUser(email);
        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());

        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Address address = addressRepository.findByIdAndUserId(request.getAddressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        // Validate stock and deduct
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            if (p.getStock() < ci.getQuantity()) {
                throw new BadRequestException("Insufficient stock for: " + p.getName());
            }
            p.setStock(p.getStock() - ci.getQuantity());
            productRepository.save(p);
        }

        // Calculate totals
        BigDecimal subtotal = cartItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delivery = subtotal.compareTo(FREE_DELIVERY_THRESHOLD) > 0
                ? BigDecimal.ZERO : DELIVERY_CHARGE;

        Order.PaymentMethod paymentMethod = Order.PaymentMethod.valueOf(
                request.getPaymentMethod().toUpperCase());

        // Build order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .status(Order.OrderStatus.PENDING)
                .subtotal(subtotal)
                .deliveryCharge(delivery)
                .total(subtotal.add(delivery))
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentMethod == Order.PaymentMethod.COD
                        ? Order.PaymentStatus.PENDING
                        : Order.PaymentStatus.PAID)
                .addressFullName(address.getFullName())
                .addressStreet(address.getStreet())
                .addressCity(address.getCity())
                .addressPostalCode(address.getPostalCode())
                .addressCountry(address.getCountry())
                .expectedDeliveryDate(LocalDateTime.now().plusDays(5))
                .build();

        // Add order items
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            OrderItem oi = OrderItem.builder()
                    .order(order)
                    .product(p)
                    .productName(p.getName())
                    .productImageUrl(p.getImageUrl())
                    .categoryName(p.getCategory().getName())
                    .unitPrice(p.getPrice())
                    .quantity(ci.getQuantity())
                    .lineTotal(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                    .build();
            order.getItems().add(oi);
        }

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteByUserId(user.getId());

        return toDto(saved);
    }

    public PageResponse<OrderDto> getUserOrders(String email, int page, int size) {
        User user = getUser(email);
        return PageResponse.from(
                orderRepository.findByUserIdOrderByPlacedAtDesc(
                        user.getId(), PageRequest.of(page, size))
                        .map(this::toDto));
    }

    public OrderDto getOrderDetail(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return toDto(order);
    }

    @Transactional
    public OrderDto cancelOrder(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() == Order.OrderStatus.DELIVERED ||
            order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot cancel order in status: " + order.getStatus());
        }

        // Restore stock
        order.getItems().forEach(item -> {
            Product p = item.getProduct();
            p.setStock(p.getStock() + item.getQuantity());
            productRepository.save(p);
        });

        order.setStatus(Order.OrderStatus.CANCELLED);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderDto requestReturn(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new BadRequestException("Can only return delivered orders");
        }

        order.setReturnRequested(true);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderDto requestExchange(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new BadRequestException("Can only exchange delivered orders");
        }

        order.setExchangeRequested(true);
        return toDto(orderRepository.save(order));
    }

    // Admin: update order status
    @Transactional
    public OrderDto updateStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(Order.OrderStatus.valueOf(status.toUpperCase()));
        return toDto(orderRepository.save(order));
    }

    private String generateOrderNumber() {
        String orderNumber;
        do {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private OrderDto toDto(Order o) {
        List<OrderDto.OrderItemDto> items = o.getItems().stream()
                .map(i -> OrderDto.OrderItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProductName())
                        .productImageUrl(i.getProductImageUrl())
                        .categoryName(i.getCategoryName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build())
                .toList();

        return OrderDto.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus().name())
                .subtotal(o.getSubtotal())
                .deliveryCharge(o.getDeliveryCharge())
                .total(o.getTotal())
                .paymentMethod(o.getPaymentMethod().name())
                .paymentStatus(o.getPaymentStatus().name())
                .addressFullName(o.getAddressFullName())
                .addressStreet(o.getAddressStreet())
                .addressCity(o.getAddressCity())
                .addressPostalCode(o.getAddressPostalCode())
                .addressCountry(o.getAddressCountry())
                .expectedDeliveryDate(o.getExpectedDeliveryDate())
                .returnRequested(o.getReturnRequested())
                .exchangeRequested(o.getExchangeRequested())
                .items(items)
                .placedAt(o.getPlacedAt())
                .build();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
