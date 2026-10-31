package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ServiceRequestCreateRequest;
import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.OrderItem;
import com.nintails.ecommerce.entity.ServiceRequest;
import com.nintails.ecommerce.entity.SellerFulfillment;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.WarehouseLocation;
import com.nintails.ecommerce.repository.OrderItemRepository;
import com.nintails.ecommerce.repository.OrderRepository;
import com.nintails.ecommerce.repository.ServiceRequestRepository;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.repository.WarehouseLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {

    @Mock private ServiceRequestRepository requestRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private WarehouseLocationRepository warehouseRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private ServiceRequestService service;

    private User customer;
    private Order order;
    private OrderItem item;
    private SellerFulfillment fulfillment;
    private WarehouseLocation warehouse;

    @BeforeEach
    void setUp() {
        customer = User.builder().id(10L).email("customer@example.com")
                .fullName("Customer").role(User.Role.USER).build();
        User owner = User.builder().id(20L).fullName("Seller")
                .role(User.Role.OWNER).build();
        User operator = User.builder().id(30L).fullName("Warehouse")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        warehouse = WarehouseLocation.builder().id(40L).name("Destination Hub")
                .street("Warehouse Road").city("Bengaluru").postalCode("560001")
                .active(true).operator(operator).build();
        order = Order.builder().id(50L).orderNumber("ORD-TEST-50")
                .user(customer).addressFullName("Customer").build();
        fulfillment = SellerFulfillment.builder().id(60L).order(order).owner(owner)
                .status(SellerFulfillment.FulfillmentStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now().minusDays(1))
                .destinationWarehouseLocation(warehouse).build();
        item = OrderItem.builder().id(70L).order(order).fulfillment(fulfillment)
                .productName("Test product").quantity(1).cancelled(false).build();
    }

    @Test
    void customerCanSubmitReturnRequestForDeliveredItem() {
        assertRequestCreation(ServiceRequest.Type.RETURN);

        assertTrue(Boolean.TRUE.equals(order.getReturnRequested()));
        assertEquals(Boolean.FALSE, order.getExchangeRequested());
    }

    @Test
    void customerCanSubmitExchangeRequestForDeliveredItem() {
        assertRequestCreation(ServiceRequest.Type.EXCHANGE);

        assertTrue(Boolean.TRUE.equals(order.getExchangeRequested()));
        assertEquals(Boolean.FALSE, order.getReturnRequested());
    }

    private void assertRequestCreation(ServiceRequest.Type type) {
        ServiceRequestCreateRequest request = new ServiceRequestCreateRequest();
        request.setType(type);
        request.setReason(type == ServiceRequest.Type.RETURN
                ? ServiceRequest.Reason.QUALITY_NOT_AS_EXPECTED
                : ServiceRequest.Reason.SIZE_OR_COLOR_CHANGE);
        request.setDetails("The product needs attention.");
        request.setPhotoUrl("http://localhost:8081/api/uploads/order-evidence/123e4567-e89b-12d3-a456-426614174000.jpg");
        request.setVideoUrl("http://localhost:8081/api/uploads/order-evidence/123e4567-e89b-12d3-a456-426614174001.mp4");
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndUserId(order.getId(), customer.getId()))
                .thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderAndUserForUpdate(
                item.getId(), order.getId(), customer.getId())).thenReturn(Optional.of(item));
        when(requestRepository.findByOrderItem_Order_IdOrderByRequestedAtDesc(order.getId()))
                .thenReturn(List.of());
        when(requestRepository.save(any(ServiceRequest.class)))
                .thenAnswer(invocation -> {
                    ServiceRequest saved = invocation.getArgument(0);
                    saved.setId(80L);
                    return saved;
                });

        var result = service.create(customer.getEmail(), order.getId(), item.getId(), request);

        assertEquals(type, result.getType());
        assertEquals(item.getId(), result.getOrderItemId());
        assertEquals(warehouse.getId(), result.getWarehouseId());
        verify(requestRepository).save(any(ServiceRequest.class));
        verify(notificationService, times(2)).notify(any(User.class), any(Order.class),
                any(SellerFulfillment.class), any(String.class), any(String.class), any(String.class));
    }
}
