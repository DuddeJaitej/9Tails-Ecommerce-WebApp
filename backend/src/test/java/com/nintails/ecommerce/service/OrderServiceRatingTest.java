package com.nintails.ecommerce.service;

import com.nintails.ecommerce.entity.OrderItem;
import com.nintails.ecommerce.entity.SellerFulfillment;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.repository.AddressRepository;
import com.nintails.ecommerce.repository.CartItemRepository;
import com.nintails.ecommerce.repository.OrderItemRepository;
import com.nintails.ecommerce.repository.OrderRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceRatingTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private RazorpayPaymentGateway razorpayPaymentGateway;
    @Mock private NotificationService notificationService;
    @Mock private DeliveryWorkflowService deliveryWorkflowService;
    @Mock private ServiceRequestService serviceRequestService;

    @InjectMocks private OrderService orderService;

    @Test
    void customerCanRateAnItemAfterItsFulfillmentIsDelivered() {
        OrderItem item = OrderItem.builder()
                .id(22L)
                .fulfillment(SellerFulfillment.builder()
                        .status(SellerFulfillment.FulfillmentStatus.DELIVERED)
                        .build())
                .build();
        givenOwnedItem(item);

        assertEquals(5, orderService.rateOrderItem(
                "buyer@example.com", 11L, 22L, 5, "  Loved it!  "));
        assertEquals(5, item.getCustomerRating());
        assertEquals("Loved it!", item.getCustomerReview());
        verify(orderItemRepository).save(item);
    }

    @Test
    void authenticatedNonCustomerRoleCanRateItsOwnDeliveredOrder() {
        OrderItem item = OrderItem.builder()
                .id(22L)
                .fulfillment(SellerFulfillment.builder()
                        .status(SellerFulfillment.FulfillmentStatus.DELIVERED)
                        .build())
                .build();
        User owner = User.builder()
                .id(7L)
                .email("buyer@example.com")
                .role(User.Role.OWNER)
                .build();
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(owner));
        when(orderItemRepository.findByIdAndOrderAndUserForUpdate(22L, 11L, 7L))
                .thenReturn(Optional.of(item));

        assertEquals(4, orderService.rateOrderItem(
                "buyer@example.com", 11L, 22L, 4, "Bought as a store owner."));
        assertEquals(4, item.getCustomerRating());
        verify(orderItemRepository).save(item);
    }

    @Test
    void customerCannotRateAnItemBeforeDelivery() {
        OrderItem item = OrderItem.builder()
                .id(22L)
                .fulfillment(SellerFulfillment.builder()
                        .status(SellerFulfillment.FulfillmentStatus.OUT_FOR_DELIVERY)
                        .build())
                .build();
        givenOwnedItem(item);

        assertThrows(BadRequestException.class,
                () -> orderService.rateOrderItem(
                        "buyer@example.com", 11L, 22L, 4, "Not delivered yet."));
        verify(orderItemRepository, never()).save(item);
    }

    @Test
    void customerCannotRateCancelledItemsOrSubmitOutOfRangeRatings() {
        OrderItem item = OrderItem.builder()
                .id(22L)
                .cancelled(true)
                .fulfillment(SellerFulfillment.builder()
                        .status(SellerFulfillment.FulfillmentStatus.DELIVERED)
                        .build())
                .build();
        givenOwnedItem(item);

        assertThrows(BadRequestException.class,
                () -> orderService.rateOrderItem(
                        "buyer@example.com", 11L, 22L, 4, "Cancelled."));
        assertThrows(BadRequestException.class,
                () -> orderService.rateOrderItem(
                        "buyer@example.com", 11L, 22L, 0, "Invalid stars."));
        assertThrows(BadRequestException.class,
                () -> orderService.rateOrderItem(
                        "buyer@example.com", 11L, 22L, 4, "   "));
        verify(orderItemRepository, never()).save(item);
    }

    private void givenOwnedItem(OrderItem item) {
        User user = User.builder().id(7L).email("buyer@example.com").build();
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(orderItemRepository.findByIdAndOrderAndUserForUpdate(22L, 11L, 7L))
                .thenReturn(Optional.of(item));
    }
}
