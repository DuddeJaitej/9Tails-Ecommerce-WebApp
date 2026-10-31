package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.OrderDto;
import com.nintails.ecommerce.dto.FulfillmentDto;
import com.nintails.ecommerce.dto.PageResponse;
import com.nintails.ecommerce.dto.PlaceOrderRequest;
import com.nintails.ecommerce.dto.RazorpayOrderResponse;
import com.nintails.ecommerce.entity.*;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final RazorpayPaymentGateway razorpayPaymentGateway;
    private final NotificationService notificationService;
    private final DeliveryWorkflowService deliveryWorkflowService;
    private final ServiceRequestService serviceRequestService;

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("10");

    @Transactional
    public OrderDto placeOrder(String email, PlaceOrderRequest request) {
        User user = getUser(email);
        Order.PaymentMethod paymentMethod = Order.PaymentMethod.valueOf(
                request.getPaymentMethod().toUpperCase());
        if (paymentMethod != Order.PaymentMethod.COD) {
            throw new BadRequestException(
                    "Online orders must be completed through Razorpay Checkout");
        }

        List<CartItem> cartItems = cartItemRepository.findByUserIdForUpdate(user.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Address address = addressRepository.findByIdAndUserId(request.getAddressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        ensureDeliveryCoordinates(address);

        // Lock products in a stable order to prevent checkout deadlocks.
        List<Product> lockedProducts = cartItems.stream()
                .map(item -> productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product", item.getProduct().getId())))
                .toList();
        for (CartItem ci : cartItems) {
            Product p = lockedProducts.stream()
                    .filter(product -> product.getId().equals(ci.getProduct().getId()))
                    .findFirst().orElseThrow();
            ci.setProduct(p);
            if (p.getStock() < ci.getQuantity()) {
                throw new BadRequestException("Insufficient stock for: " + p.getName());
            }
            p.setStock(p.getStock() - ci.getQuantity());
        }

        // Calculate totals
        BigDecimal subtotal = calculateSubtotal(cartItems);
        BigDecimal delivery = calculateDelivery(subtotal);

        // Build order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .status(Order.OrderStatus.PENDING)
                .subtotal(subtotal)
                .deliveryCharge(delivery)
                .total(subtotal.add(delivery))
                .paymentMethod(paymentMethod)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .addressFullName(address.getFullName())
                .addressStreet(address.getStreet())
                .addressCity(address.getCity())
                .addressPostalCode(address.getPostalCode())
                .addressCountry(address.getCountry())
                .addressLatitude(address.getLatitude())
                .addressLongitude(address.getLongitude())
                .build();

        // Add order items
        for (CartItem cartItem : cartItems) {
            OrderItem item = toOrderItem(order, cartItem);
            item.setStockReserved(true);
            order.getItems().add(item);
        }

        Order saved = orderRepository.save(order);
        createSellerFulfillments(saved);
        orderRepository.save(saved);
        notifySellerFulfillments(saved);
        cartItemRepository.deleteByUserId(user.getId());

        return toDto(saved);
    }

    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(String email, Long addressId) {
        User user = getUser(email);
        List<CartItem> cartItems = cartItemRepository.findByUserIdForUpdate(user.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        ensureDeliveryCoordinates(address);
        var existingPayment = orderRepository
                .findFirstByUserIdAndPaymentMethodAndPaymentStatusAndStatusOrderByPlacedAtDesc(
                        user.getId(), Order.PaymentMethod.UPI, Order.PaymentStatus.PENDING,
                        Order.OrderStatus.PENDING);
        if (existingPayment.isPresent()) {
            Order order = existingPayment.get();
            boolean sameCart = order.getItems().size() == cartItems.size()
                    && order.getItems().stream().allMatch(item -> item.isStockReserved()
                    && cartItems.stream().anyMatch(cartItem ->
                    cartItem.getProduct().getId().equals(item.getProduct().getId())
                            && cartItem.getQuantity().equals(item.getQuantity())));
            boolean sameAddress = java.util.Objects.equals(
                    order.getAddressFullName(), address.getFullName())
                    && java.util.Objects.equals(order.getAddressStreet(), address.getStreet())
                    && java.util.Objects.equals(order.getAddressCity(), address.getCity())
                    && java.util.Objects.equals(order.getAddressPostalCode(), address.getPostalCode())
                    && java.util.Objects.equals(order.getAddressCountry(), address.getCountry());
            sameAddress = sameAddress
                    && java.util.Objects.equals(order.getAddressLatitude(), address.getLatitude())
                    && java.util.Objects.equals(order.getAddressLongitude(), address.getLongitude());
            if (!sameCart || !sameAddress || order.getRazorpayOrderId() == null) {
                throw new BadRequestException(
                        "A previous online payment is still pending. Complete that checkout before starting another.");
            }
            return RazorpayOrderResponse.builder()
                    .keyId(razorpayPaymentGateway.getKeyId())
                    .orderId(order.getRazorpayOrderId())
                    .amount(order.getTotal().movePointRight(2).longValueExact())
                    .currency("INR")
                    .receipt(order.getOrderNumber())
                    .build();
        }

        List<Product> lockedProducts = cartItems.stream()
                .map(item -> productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product", item.getProduct().getId())))
                .toList();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem cartItem = cartItems.get(i);
            Product product = lockedProducts.get(i);
            cartItem.setProduct(product);
            if (product.getStock() < cartItem.getQuantity()) {
                throw new BadRequestException(
                        "Insufficient stock for: " + product.getName());
            }
        }

        BigDecimal subtotal = calculateSubtotal(cartItems);
        BigDecimal delivery = calculateDelivery(subtotal);
        BigDecimal total = subtotal.add(delivery);
        long amountPaise;
        try {
            amountPaise = total.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Order total cannot be represented in paise");
        }

        String orderNumber = generateOrderNumber();
        RazorpayPaymentGateway.CreatedRazorpayOrder razorpayOrder =
                razorpayPaymentGateway.createOrder(amountPaise, orderNumber);

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(Order.OrderStatus.PENDING)
                .subtotal(subtotal)
                .deliveryCharge(delivery)
                .total(total)
                .paymentMethod(Order.PaymentMethod.UPI)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .razorpayOrderId(razorpayOrder.orderId())
                .addressFullName(address.getFullName())
                .addressStreet(address.getStreet())
                .addressCity(address.getCity())
                .addressPostalCode(address.getPostalCode())
                .addressCountry(address.getCountry())
                .addressLatitude(address.getLatitude())
                .addressLongitude(address.getLongitude())
                .build();
        for (CartItem cartItem : cartItems) {
            OrderItem item = toOrderItem(order, cartItem);
            item.setStockReserved(true);
            order.getItems().add(item);
        }
        for (int i = 0; i < cartItems.size(); i++) {
            Product product = lockedProducts.get(i);
            product.setStock(product.getStock() - cartItems.get(i).getQuantity());
        }
        orderRepository.save(order);

        return RazorpayOrderResponse.builder()
                .keyId(razorpayPaymentGateway.getKeyId())
                .orderId(razorpayOrder.orderId())
                .amount(razorpayOrder.amount())
                .currency(razorpayOrder.currency())
                .receipt(orderNumber)
                .build();
    }

    @Transactional
    public OrderDto verifyRazorpayPayment(
            String email, String razorpayOrderId, String razorpayPaymentId, String signature) {
        User user = getUser(email);
        Order order = orderRepository.findByRazorpayOrderIdAndUserIdForUpdate(
                        razorpayOrderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Online payment order not found"));

        if (order.getPaymentStatus() == Order.PaymentStatus.PAID
                || order.getPaymentStatus() == Order.PaymentStatus.REFUND_PENDING) {
            if (razorpayPaymentId.equals(order.getRazorpayPaymentId())) {
                return toDto(order);
            }
            throw new BadRequestException("This order was already paid with a different payment");
        }

        razorpayPaymentGateway.verifySignature(
                razorpayOrderId, razorpayPaymentId, signature);

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setPaymentStatus(Order.PaymentStatus.REFUND_PENDING);
            userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN).forEach(admin ->
                    notificationService.notify(admin, order, null, "REFUND_REQUIRES_REVIEW",
                            "Payment received for a cancelled order",
                            "A payment completed after order " + order.getOrderNumber()
                                    + " was cancelled; review it for refund."));
            notificationService.notify(order.getUser(), order, null, "REFUND_REQUIRES_REVIEW",
                    "Payment needs review",
                    "Your payment arrived after this order was cancelled. The store has been notified to review it.");
            return toDto(orderRepository.save(order));
        }

        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        List<Product> lockedProducts = items.stream()
                .map(item -> productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product", item.getProduct().getId())))
                .toList();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            Product product = lockedProducts.get(i);
            if (!item.isStockReserved()) {
                throw new BadRequestException("Inventory reservation is missing for this payment order.");
            }
            cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                    .filter(cartItem -> cartItem.getQuantity().equals(item.getQuantity()))
                    .ifPresent(cartItem -> cartItemRepository.deleteByUserIdAndProductId(
                            user.getId(), product.getId()));
        }

        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setStatus(Order.OrderStatus.PENDING);
        if (order.getFulfillments().isEmpty()) {
            createSellerFulfillments(order);
            notifySellerFulfillments(order);
        }
        return toDto(orderRepository.save(order));
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
    public int rateOrderItem(String email, Long orderId, Long itemId, int rating, String reviewText) {
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Choose a rating from 1 to 5 stars.");
        }
        if (reviewText == null || reviewText.isBlank() || reviewText.trim().length() > 1000) {
            throw new BadRequestException("Write a review of up to 1,000 characters.");
        }
        User user = getUser(email);
        OrderItem item = orderItemRepository.findByIdAndOrderAndUserForUpdate(
                        itemId, orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order item", itemId));
        if (item.isCancelled()) {
            throw new BadRequestException("Cancelled items cannot be rated.");
        }
        if (item.getFulfillment() == null
                || item.getFulfillment().getStatus() != SellerFulfillment.FulfillmentStatus.DELIVERED) {
            throw new BadRequestException("You can rate this product after it has been delivered.");
        }
        item.setCustomerRating(rating);
        item.setCustomerReview(reviewText.trim());
        item.setCustomerReviewedAt(LocalDateTime.now());
        orderItemRepository.save(item);
        return rating;
    }

    @Transactional
    public OrderDto cancelOrder(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING
                && order.getStatus() != Order.OrderStatus.CONFIRMED
                && order.getStatus() != Order.OrderStatus.PACKED) {
            throw new BadRequestException("Cannot cancel order in status: " + order.getStatus());
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            throw new BadRequestException(
                    "Paid orders need a refund review before they can be cancelled.");
        }

        // Restore stock
        order.getItems().stream().filter(item -> !item.isCancelled()).forEach(item -> {
            if (item.isStockReserved()) {
                Product p = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product", item.getProduct().getId()));
                p.setStock(p.getStock() + item.getQuantity());
                item.setStockReserved(false);
            }
            item.setCancelled(true);
        });

        order.setStatus(Order.OrderStatus.CANCELLED);
        deliveryWorkflowService.revokeForOrder(order, user);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderDto cancelOrderItem(String email, Long orderId, Long itemId) {
        User user = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING &&
            order.getStatus() != Order.OrderStatus.CONFIRMED &&
            order.getStatus() != Order.OrderStatus.PACKED) {
            throw new BadRequestException("Order items can only be cancelled before the order is shipped");
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            throw new BadRequestException(
                    "Paid order items need a refund review before they can be cancelled.");
        }

        OrderItem item = order.getItems().stream()
                .filter(orderItem -> orderItem.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item", itemId));

        if (item.isCancelled()) {
            throw new BadRequestException("Order item is already cancelled");
        }

        if (item.isStockReserved()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", item.getProduct().getId()));
            product.setStock(product.getStock() + item.getQuantity());
            item.setStockReserved(false);
        }
        item.setCancelled(true);
        deliveryWorkflowService.cancelFulfillmentIfEmpty(order, item, user);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem orderItem : order.getItems()) {
            if (!orderItem.isCancelled()) {
                subtotal = subtotal.add(orderItem.getLineTotal());
            }
        }
        BigDecimal delivery = subtotal.compareTo(FREE_DELIVERY_THRESHOLD) > 0
                ? BigDecimal.ZERO : DELIVERY_CHARGE;
        order.setSubtotal(subtotal);
        order.setDeliveryCharge(subtotal.signum() == 0 ? BigDecimal.ZERO : delivery);
        order.setTotal(order.getSubtotal().add(order.getDeliveryCharge()));

        if (subtotal.signum() == 0) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            deliveryWorkflowService.revokeForOrder(order, user);
        }

        return toDto(orderRepository.save(order));
    }

    // Admin: update order status
    @Transactional
    public OrderDto updateStatus(String adminEmail, Long orderId, String status) {
        User administrator = getUser(adminEmail);
        if (administrator.getRole() != User.Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
        if (!"CANCELLED".equalsIgnoreCase(status)) {
            throw new BadRequestException(
                    "Order delivery statuses must be advanced through the audited fulfillment workflow.");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() == Order.OrderStatus.DELIVERED
                || order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BadRequestException("This order cannot be cancelled.");
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            order.setPaymentStatus(Order.PaymentStatus.REFUND_PENDING);
            userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN).forEach(admin ->
                    notificationService.notify(admin, order, null, "REFUND_REQUIRES_REVIEW",
                            "Order cancellation needs refund review",
                            "A paid order was cancelled by an administrator; review the payment for order "
                                    + order.getOrderNumber() + "."));
        }
        order.getItems().stream().filter(item -> !item.isCancelled()).forEach(item -> {
            if (item.isStockReserved()) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", item.getProduct().getId()));
                product.setStock(product.getStock() + item.getQuantity());
                item.setStockReserved(false);
            }
            item.setCancelled(true);
        });
        deliveryWorkflowService.revokeForOrder(order, administrator);
        order.setStatus(Order.OrderStatus.CANCELLED);
        return toDto(orderRepository.save(order));
    }

    private String generateOrderNumber() {
        String orderNumber;
        do {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private BigDecimal calculateSubtotal(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(item -> item.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDelivery(BigDecimal subtotal) {
        return subtotal.compareTo(FREE_DELIVERY_THRESHOLD) > 0
                ? BigDecimal.ZERO : DELIVERY_CHARGE;
    }

    private OrderItem toOrderItem(Order order, CartItem cartItem) {
        Product product = cartItem.getProduct();
        return OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .productImageUrl(product.getImageUrl())
                .categoryName(product.getCategory().getName())
                .unitPrice(product.getPrice())
                .quantity(cartItem.getQuantity())
                .lineTotal(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                .build();
    }

    private void createSellerFulfillments(Order order) {
        Map<String, SellerFulfillment> fulfillmentsBySeller = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            User owner = item.getProduct().getOwner();
            String sellerKey = owner == null ? "ADMIN" : "USER:" + owner.getId();
            SellerFulfillment fulfillment = fulfillmentsBySeller.computeIfAbsent(sellerKey, key -> {
                SellerFulfillment created = SellerFulfillment.builder()
                        .order(order)
                        .owner(owner)
                        .sellerKey(key)
                        .status(SellerFulfillment.FulfillmentStatus.AWAITING_OWNER_CONFIRMATION)
                        .build();
                BusinessLocation location = item.getProduct().getBusinessLocation();
                if (location != null) {
                    created.setPickupLocation(location);
                    created.setPickupName(location.getName());
                    created.setPickupStreet(location.getStreet());
                    created.setPickupCity(location.getCity());
                    created.setPickupState(location.getState());
                    created.setPickupPostalCode(location.getPostalCode());
                    created.setPickupCountry(location.getCountry());
                }
                order.getFulfillments().add(created);
                return created;
            });
            item.setFulfillment(fulfillment);
            fulfillment.getItems().add(item);
        }
    }

    private void notifySellerFulfillments(Order order) {
        for (SellerFulfillment fulfillment : order.getFulfillments()) {
            String title = "New order awaiting confirmation";
            String message = "Order " + order.getOrderNumber() + " contains "
                    + fulfillment.getItems().size() + " item(s) for your fulfillment.";
            if (fulfillment.getOwner() != null) {
                notificationService.notify(fulfillment.getOwner(), order, fulfillment,
                        "ORDER_AWAITING_CONFIRMATION", title, message);
            } else {
                userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN)
                        .forEach(admin -> notificationService.notify(admin, order, fulfillment,
                                "ORDER_AWAITING_CONFIRMATION", title, message));
            }
        }
        notificationService.notify(order.getUser(), order, null, "ORDER_PLACED",
                "Order placed", "Your order " + order.getOrderNumber() + " has been received.");
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
                        .cancelled(i.isCancelled())
                        .fulfillmentStatus(i.getFulfillment() == null
                                ? null : i.getFulfillment().getStatus().name())
                        .deliveredAt(i.getFulfillment() == null
                                ? null : i.getFulfillment().getDeliveredAt())
                        .rating(i.getCustomerRating())
                        .reviewText(i.getCustomerReview())
                        .build())
                .toList();
        List<FulfillmentDto> fulfillments = o.getFulfillments().stream()
                .map(fulfillment -> FulfillmentDto.builder()
                        .id(fulfillment.getId())
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .status(fulfillment.getStatus().name())
                        .ownerName(fulfillment.getOwner() == null
                                ? "9Tails Store" : fulfillment.getOwner().getFullName())
                        .deliveryAgentId(fulfillment.getDeliveryAgent() == null
                                ? null : fulfillment.getDeliveryAgent().getId())
                        .deliveryAgentName(fulfillment.getDeliveryAgent() == null
                                ? null : fulfillment.getDeliveryAgent().getFullName())
                        .deliveryAgentOfferExpiresAt(fulfillment.getDeliveryAgentOfferExpiresAt())
                        .deliveryAgentAcceptedAt(fulfillment.getDeliveryAgentAcceptedAt())
                        .deliveryAgentRouteDeadline(fulfillment.getDeliveryAgentRouteDeadline())
                        .transferDeliveryAgentId(fulfillment.getTransferDeliveryAgent() == null
                                ? null : fulfillment.getTransferDeliveryAgent().getId())
                        .transferDeliveryAgentName(fulfillment.getTransferDeliveryAgent() == null
                                ? null : fulfillment.getTransferDeliveryAgent().getFullName())
                        .transferAgentOfferExpiresAt(fulfillment.getTransferAgentOfferExpiresAt())
                        .transferAgentAcceptedAt(fulfillment.getTransferAgentAcceptedAt())
                        .transferAgentRouteDeadline(fulfillment.getTransferAgentRouteDeadline())
                        .finalDeliveryAgentId(fulfillment.getFinalDeliveryAgent() == null
                                ? null : fulfillment.getFinalDeliveryAgent().getId())
                        .finalDeliveryAgentName(fulfillment.getFinalDeliveryAgent() == null
                                ? null : fulfillment.getFinalDeliveryAgent().getFullName())
                        .finalAgentOfferExpiresAt(fulfillment.getFinalAgentOfferExpiresAt())
                        .finalAgentAcceptedAt(fulfillment.getFinalAgentAcceptedAt())
                        .finalAgentRouteDeadline(fulfillment.getFinalAgentRouteDeadline())
                        .warehouseLocationId(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getId())
                        .warehouseName(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getName())
                        .warehouseStreet(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getStreet())
                        .warehouseCity(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getCity())
                        .warehousePostalCode(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getPostalCode())
                        .warehouseLatitude(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getLatitude())
                        .warehouseLongitude(fulfillment.getWarehouseLocation() == null
                                ? null : fulfillment.getWarehouseLocation().getLongitude())
                        .destinationWarehouseLocationId(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getId())
                        .destinationWarehouseName(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getName())
                        .destinationWarehouseStreet(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getStreet())
                        .destinationWarehouseCity(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getCity())
                        .destinationWarehousePostalCode(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getPostalCode())
                        .destinationWarehouseLatitude(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getLatitude())
                        .destinationWarehouseLongitude(
                                fulfillment.getDestinationWarehouseLocation() == null ? null
                                        : fulfillment.getDestinationWarehouseLocation().getLongitude())
                        .customerName(o.getAddressFullName())
                        .customerStreet(o.getAddressStreet())
                        .customerCity(o.getAddressCity())
                        .customerPostalCode(o.getAddressPostalCode())
                        .serviceRequests(serviceRequestService.forFulfillment(fulfillment.getId()))
                        .customerLatitude(o.getAddressLatitude())
                        .customerLongitude(o.getAddressLongitude())
                        .pickupName(fulfillment.getPickupName())
                        .pickupStreet(fulfillment.getPickupStreet())
                        .pickupCity(fulfillment.getPickupCity())
                        .pickupState(fulfillment.getPickupState())
                        .pickupPostalCode(fulfillment.getPickupPostalCode())
                        .pickupLatitude(fulfillment.getPickupLocation() == null ? null
                                : fulfillment.getPickupLocation().getLatitude())
                        .pickupLongitude(fulfillment.getPickupLocation() == null ? null
                                : fulfillment.getPickupLocation().getLongitude())
                        .placedAt(o.getPlacedAt())
                        .expectedDeliveryDate(fulfillment.getExpectedDeliveryDate())
                        .ownerRespondedAt(fulfillment.getOwnerRespondedAt())
                        .pickupAt(fulfillment.getPickupAt())
                        .warehouseReceivedAt(fulfillment.getWarehouseReceivedAt())
                        .dispatchedAt(fulfillment.getDispatchedAt())
                        .confirmationRequestedAt(fulfillment.getConfirmationRequestedAt())
                        .deliveredAt(fulfillment.getDeliveredAt())
                        .rejectionReason(fulfillment.getRejectionReason())
                        .items(fulfillment.getItems().stream()
                                .map(item -> FulfillmentDto.Item.builder()
                                        .productName(item.getProductName())
                                        .imageUrl(item.getProductImageUrl())
                                        .quantity(item.getQuantity())
                                        .build())
                                .toList())
                        .serviceRequests(serviceRequestService.forFulfillment(fulfillment.getId()))
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
                .serviceRequests(serviceRequestService.forOrder(o.getId()))
                .items(items)
                .fulfillments(fulfillments)
                .placedAt(o.getPlacedAt())
                .build();
    }

    private void ensureDeliveryCoordinates(Address address) {
        if (address.getLatitude() == null || address.getLongitude() == null) {
            throw new BadRequestException(
                    "Pin your delivery address on the map before placing an order.");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
