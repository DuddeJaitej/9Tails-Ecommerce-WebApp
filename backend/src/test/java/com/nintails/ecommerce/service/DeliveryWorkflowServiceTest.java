package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.StageResultDto;
import com.nintails.ecommerce.dto.OwnerPickupQrTokenDto;
import com.nintails.ecommerce.entity.DeliveryStageToken;
import com.nintails.ecommerce.entity.DeliveryStageToken.Stage;
import com.nintails.ecommerce.entity.DeliveryEvent;
import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.SellerFulfillment;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.WarehouseLocation;
import com.nintails.ecommerce.entity.BusinessLocation;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.repository.*;
import com.nintails.ecommerce.security.SecureTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryWorkflowServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SellerFulfillmentRepository fulfillmentRepository;
    @Mock private DeliveryStageTokenRepository tokenRepository;
    @Mock private DeliveryEventRepository eventRepository;
    @Mock private BusinessLocationRepository businessLocationRepository;
    @Mock private WarehouseLocationRepository warehouseLocationRepository;
    @Mock private NotificationService notificationService;
    @Mock private SecureTokenService secureTokenService;
    @Mock private ServiceRequestService serviceRequestService;
    @InjectMocks private DeliveryWorkflowService service;

    private User agent;
    private User transferAgent;
    private User finalAgent;
    private User customer;
    private WarehouseLocation originWarehouse;
    private WarehouseLocation destinationWarehouse;
    private User originWarehouseOperator;
    private User destinationWarehouseOperator;
    private SellerFulfillment fulfillment;
    private DeliveryStageToken token;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "qrExpiryHours", 168L);
        originWarehouseOperator = User.builder().id(21L).email("origin-warehouse@example.test")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        destinationWarehouseOperator = User.builder().id(22L).email("destination-warehouse@example.test")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        originWarehouse = WarehouseLocation.builder().id(101L).name("Origin Hub")
                .active(true).operator(originWarehouseOperator).build();
        destinationWarehouse = WarehouseLocation.builder().id(102L).name("Destination Hub")
                .active(true).operator(destinationWarehouseOperator).build();
        agent = User.builder().id(8L).fullName("Assigned Agent")
                .email("agent@example.test").role(User.Role.DELIVERY_AGENT)
                .deliveryWarehouse(originWarehouse).deliveryAgentActive(true).build();
        transferAgent = User.builder().id(10L).fullName("Transfer Agent")
                .email("transfer-agent@example.test").role(User.Role.DELIVERY_AGENT)
                .deliveryWarehouse(originWarehouse).deliveryAgentActive(true).build();
        finalAgent = User.builder().id(9L).fullName("Final Delivery Agent")
                .email("final-agent@example.test").role(User.Role.DELIVERY_AGENT)
                .deliveryWarehouse(destinationWarehouse).deliveryAgentActive(true).build();
        User owner = User.builder().id(3L).fullName("Seller")
                .email("owner@example.test").role(User.Role.OWNER).build();
        customer = User.builder().id(12L).fullName("Customer")
                .email("customer@example.test").role(User.Role.USER)
                .build();
        Order order = Order.builder().id(19L).orderNumber("ORD-TEST-19")
                .user(customer)
                .status(Order.OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN).deliveryCharge(BigDecimal.ZERO)
                .total(BigDecimal.TEN).build();
        LocalDateTime acceptedAt = LocalDateTime.now().minusMinutes(1);
        fulfillment = SellerFulfillment.builder().id(31L).order(order).owner(owner)
                .warehouseLocation(originWarehouse)
                .destinationWarehouseLocation(destinationWarehouse)
                .deliveryAgent(agent).deliveryAgentAcceptedAt(acceptedAt)
                .deliveryAgentRouteDeadline(LocalDateTime.now().plusHours(12))
                .finalDeliveryAgent(finalAgent).finalAgentAcceptedAt(acceptedAt)
                .finalAgentRouteDeadline(LocalDateTime.now().plusHours(12))
                .status(SellerFulfillment.FulfillmentStatus.CONFIRMED)
                .pickupStreet("12 Seller Street").pickupPostalCode("560001")
                .expectedDeliveryDate(LocalDateTime.now().plusDays(5)).build();
        token = DeliveryStageToken.builder().id(40L).fulfillment(fulfillment)
                .stage(Stage.PICKUP_FROM_OWNER).tokenHash("qr-hash")
                .tokenCiphertext("encrypted").expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private void stubAgentAndLockedFulfillment(User actor) {
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
    }

    private void stubAgentAndLockedFulfillment() {
        stubAgentAndLockedFulfillment(agent);
    }

    private void stubWarehouseOperator(User operator, WarehouseLocation warehouse) {
        when(userRepository.findByEmail(operator.getEmail())).thenReturn(Optional.of(operator));
        when(warehouseLocationRepository.findByOperatorId(operator.getId()))
                .thenReturn(Optional.of(warehouse));
    }

    private void stubStageSubmission() {
        stubStageSubmission(agent);
    }

    private void stubStageSubmission(User actor) {
        stubAgentAndLockedFulfillment(actor);
        when(tokenRepository.findByTokenHashForUpdate("qr-hash")).thenReturn(Optional.of(token));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");
    }

    private void stubSuccessfulStageProgression() {
        stubStageSubmission();
        stubSuccessfulStageProgressionStubs();
    }

    private void stubSuccessfulStageProgression(User actor) {
        stubStageSubmission(actor);
        stubSuccessfulStageProgressionStubs();
    }

    private void stubSuccessfulStageProgressionStubs() {
        when(eventRepository.save(any(DeliveryEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fulfillmentRepository.findAllByOrderId(19L)).thenReturn(List.of(fulfillment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCanRegeneratePickupQrWhenSavedTokenCannotBeDecrypted() {
        User owner = fulfillment.getOwner();
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(fulfillmentRepository.findById(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(tokenRepository.findTopByFulfillmentIdAndStageOrderByIssuedAtDesc(
                fulfillment.getId(), Stage.PICKUP_FROM_OWNER)).thenReturn(Optional.of(token));
        when(secureTokenService.decrypt("encrypted"))
                .thenThrow(new IllegalStateException("Unable to decrypt delivery token"));
        when(secureTokenService.newQrToken()).thenReturn("replacement-token");
        when(secureTokenService.hashToken("replacement-token")).thenReturn("replacement-hash");
        when(secureTokenService.encrypt("replacement-token")).thenReturn("replacement-ciphertext");
        when(tokenRepository.save(any(DeliveryStageToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secureTokenService.decrypt("replacement-ciphertext")).thenReturn("replacement-token");

        OwnerPickupQrTokenDto result = service.getQrToken(
                owner.getEmail(), fulfillment.getId(), Stage.PICKUP_FROM_OWNER);

        assertEquals("replacement-token", result.token());
        assertTrue(result.refreshed());
        assertNotNull(token.getRevokedAt());
        assertEquals("replacement-hash", fulfillment.getStageTokens().get(0).getTokenHash());
        verify(eventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void onePackageQrAdvancesThroughPickupWarehouseAndDispatchStages() {
        com.nintails.ecommerce.dto.StageSubmissionRequest request =
                new com.nintails.ecommerce.dto.StageSubmissionRequest();
        request.setToken("raw-token");
        request.setNotes("Package collected");
        stubSuccessfulStageProgression();

        StageResultDto result = service.submitStage(agent.getEmail(), "raw-token", request,
                Stage.PICKUP_FROM_OWNER);

        assertEquals(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER,
                fulfillment.getStatus());
        assertNotNull(fulfillment.getPickupAt());
        assertEquals(fulfillment.getPickupAt(), result.getFulfillment().getPickupAt());
        assertNull(token.getUsedAt());
        assertEquals("WAREHOUSE_RECEIVED", result.getNextStage());
        assertNull(result.getNextQrToken());
        assertEquals(Order.OrderStatus.PICKED_UP_FROM_OWNER, fulfillment.getOrder().getStatus());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(), eq(fulfillment),
                eq("PICKED_UP_FROM_OWNER"), any(), any());
        assertThrows(ConflictException.class, () -> service.submitStage(
                agent.getEmail(), "raw-token", request, Stage.PICKUP_FROM_OWNER));
        verify(notificationService, times(1)).notify(eq(fulfillment.getOrder().getUser()), any(),
                eq(fulfillment), eq("PICKED_UP_FROM_OWNER"), any(), any());

        when(tokenRepository.findByTokenHash("qr-hash")).thenReturn(Optional.of(token));
        ConflictException packageQrCannotConfirmWarehouseArrival = assertThrows(
                ConflictException.class,
                () -> service.validateQr(agent.getEmail(), "raw-token"));
        assertTrue(packageQrCannotConfirmWarehouseArrival.getMessage().contains(
                "warehouse arrival QR"));

        fulfillment.setDeliveryAgentRouteDeadline(LocalDateTime.now().plusHours(12));
        when(secureTokenService.hashToken("9TAILS-WH:origin-qr"))
                .thenReturn("origin-warehouse-qr-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue(
                "origin-warehouse-qr-hash")).thenReturn(Optional.of(originWarehouse));
        com.nintails.ecommerce.dto.WarehouseArrivalRequest arrivalRequest =
                new com.nintails.ecommerce.dto.WarehouseArrivalRequest();
        arrivalRequest.setToken("9TAILS-WH:origin-qr");
        arrivalRequest.setFulfillmentId(fulfillment.getId());
        service.confirmWarehouseArrival(agent.getEmail(), arrivalRequest);
        assertEquals(SellerFulfillment.FulfillmentStatus.PACKED_READY, fulfillment.getStatus());
        assertNull(token.getUsedAt());
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(tokenRepository.findByTokenHash("qr-hash")).thenReturn(Optional.of(token));
        assertThrows(AccessDeniedException.class,
                () -> service.validateQr(originWarehouseOperator.getEmail(), "raw-token"));

        when(userRepository.findByIdAndRole(transferAgent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(transferAgent));
        service.assignTransferDeliveryAgent(originWarehouseOperator.getEmail(),
                fulfillment.getId(), transferAgent.getId());
        stubAgentAndLockedFulfillment(transferAgent);
        service.acceptDeliveryOffer(transferAgent.getEmail(), fulfillment.getId());
        stubSuccessfulStageProgression(transferAgent);
        assertEquals("OUT_FOR_DELIVERY_TO_STORE",
                service.validateQr(transferAgent.getEmail(), "raw-token").getStage());
        service.submitStage(transferAgent.getEmail(), "raw-token", request,
                Stage.OUT_FOR_DELIVERY_TO_STORE);
        assertEquals(SellerFulfillment.FulfillmentStatus.IN_TRANSIT_TO_STORE, fulfillment.getStatus());

        com.nintails.ecommerce.dto.WarehouseArrivalRequest destinationArrival =
                new com.nintails.ecommerce.dto.WarehouseArrivalRequest();
        destinationArrival.setToken("9TAILS-WH:destination-qr");
        destinationArrival.setFulfillmentId(fulfillment.getId());
        when(secureTokenService.hashToken("9TAILS-WH:destination-qr"))
                .thenReturn("destination-warehouse-qr-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue(
                "destination-warehouse-qr-hash")).thenReturn(Optional.of(destinationWarehouse));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        service.confirmWarehouseArrival(transferAgent.getEmail(), destinationArrival);
        assertEquals(SellerFulfillment.FulfillmentStatus.AT_DESTINATION_STORE, fulfillment.getStatus());

        stubWarehouseOperator(destinationWarehouseOperator, destinationWarehouse);
        when(userRepository.findByIdAndRole(finalAgent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(finalAgent));
        service.assignFinalDeliveryAgent(destinationWarehouseOperator.getEmail(),
                fulfillment.getId(), finalAgent.getId());
        stubAgentAndLockedFulfillment(finalAgent);
        service.acceptDeliveryOffer(finalAgent.getEmail(), fulfillment.getId());
        assertEquals("OUT_FOR_DELIVERY", service.validateQr(finalAgent.getEmail(), "raw-token").getStage());

        service.submitStage(finalAgent.getEmail(), "raw-token", request, Stage.OUT_FOR_DELIVERY);
        assertEquals(SellerFulfillment.FulfillmentStatus.OUT_FOR_DELIVERY, fulfillment.getStatus());
        assertNull(token.getUsedAt());
        assertEquals("DELIVERED", service.validateQr(finalAgent.getEmail(), "raw-token").getStage());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(), eq(fulfillment),
                eq("WAREHOUSE_RECEIVED"), any(), any());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(), eq(fulfillment),
                eq("IN_TRANSIT_TO_STORE"), any(), any());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(), eq(fulfillment),
                eq("AT_DESTINATION_STORE"), any(), any());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(), eq(fulfillment),
                eq("OUT_FOR_DELIVERY"), any(), any());
    }

    @Test
    void ownerCanAssignTheirVerifiedPickupLocationToAnExistingPendingFulfillment() {
        User owner = fulfillment.getOwner();
        BusinessLocation location = BusinessLocation.builder()
                .id(55L)
                .owner(owner)
                .name("Seller Pickup")
                .street("12 Seller Street")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560001")
                .country("India")
                .contactPhone("9876543210")
                .latitude(12.9716)
                .longitude(77.5946)
                .verifiedByOwner(true)
                .build();
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.AWAITING_OWNER_CONFIRMATION);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(businessLocationRepository.findByIdAndOwnerId(location.getId(), owner.getId()))
                .thenReturn(Optional.of(location));
        when(fulfillmentRepository.save(fulfillment)).thenReturn(fulfillment);

        com.nintails.ecommerce.dto.FulfillmentDto result =
                service.setPickupLocation(owner.getEmail(), fulfillment.getId(), location.getId());

        assertEquals(location.getName(), fulfillment.getPickupName());
        assertEquals(location.getStreet(), fulfillment.getPickupStreet());
        assertEquals(location.getLatitude(), result.getPickupLatitude());
        assertEquals(location.getLongitude(), result.getPickupLongitude());
        verify(fulfillmentRepository).save(fulfillment);
    }

    @Test
    void wrongStageTokenIsRejectedWithoutConsumingIt() {
        var request = new com.nintails.ecommerce.dto.StageSubmissionRequest();
        request.setToken("raw-token");
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(tokenRepository.findByTokenHashForUpdate("qr-hash")).thenReturn(Optional.of(token));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");

        assertThrows(ConflictException.class, () -> service.submitStage(agent.getEmail(),
                "raw-token", request, Stage.WAREHOUSE_RECEIVED));
        assertNull(token.getUsedAt());
        assertEquals(SellerFulfillment.FulfillmentStatus.CONFIRMED, fulfillment.getStatus());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void originWarehouseOperatorCanAssignAgentAfterAcceptingPickup() {
        fulfillment.setDeliveryAgent(null);
        fulfillment.setDeliveryAgentAcceptedAt(null);
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(eventRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillment.getId()))
                .thenReturn(List.of(warehouseDecision("WAREHOUSE_PICKUP_ACCEPTED", originWarehouse)));
        when(userRepository.findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(agent));

        com.nintails.ecommerce.dto.FulfillmentDto result =
                service.assignDeliveryAgent(originWarehouseOperator.getEmail(),
                        fulfillment.getId(), agent.getId());

        assertEquals(agent.getId(), result.getDeliveryAgentId());
        assertNotNull(result.getDeliveryAgentOfferExpiresAt());
        verify(notificationService).notify(eq(agent), eq(fulfillment.getOrder()), eq(fulfillment),
                eq("DELIVERY_ASSIGNMENT"), any(), any());
    }

    @Test
    void originWarehouseOperatorMustAcceptPickupBeforeAssigningAgent() {
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));

        assertThrows(ConflictException.class, () -> service.assignDeliveryAgent(
                originWarehouseOperator.getEmail(), fulfillment.getId(), agent.getId()));
        verify(userRepository, never()).findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT);
    }

    @Test
    void originWarehouseOperatorCanAcceptPickupRequestAndDecisionIsPersisted() {
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(eventRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillment.getId()))
                .thenReturn(List.of(), List.of(warehouseDecision(
                        "WAREHOUSE_PICKUP_ACCEPTED", originWarehouse)));
        when(eventRepository.save(any(DeliveryEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptWarehousePickup(
                originWarehouseOperator.getEmail(), fulfillment.getId());

        assertEquals("ACCEPTED", result.getWarehousePickupDecision());
        verify(notificationService).notify(eq(fulfillment.getOwner()), eq(fulfillment.getOrder()),
                eq(fulfillment), eq("WAREHOUSE_PICKUP_ACCEPTED"), any(), any());
    }

    @Test
    void decliningPickupReroutesToNextNearestStaffedWarehouseAndRecordsReason() {
        originWarehouse.setLatitude(12.0);
        originWarehouse.setLongitude(77.0);
        destinationWarehouse.setLatitude(13.0);
        destinationWarehouse.setLongitude(78.0);
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        DeliveryEvent rejection = warehouseDecision("WAREHOUSE_PICKUP_REJECTED", originWarehouse);
        rejection.setNotes("warehouse:101: Rejecting due to no pickup capacity.");
        when(eventRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillment.getId()))
                .thenReturn(List.of(), List.of(rejection), List.of(rejection));
        when(warehouseLocationRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(originWarehouse, destinationWarehouse));
        when(eventRepository.save(any(DeliveryEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rejectWarehousePickup(
                originWarehouseOperator.getEmail(), fulfillment.getId(), "No pickup capacity");

        assertEquals(destinationWarehouse.getId(), fulfillment.getWarehouseLocation().getId());
        assertEquals("PENDING", result.getWarehousePickupDecision());
        verify(notificationService).notify(eq(destinationWarehouseOperator), eq(fulfillment.getOrder()),
                eq(fulfillment), eq("PICKUP_AGENT_ASSIGNMENT_REQUIRED"), any(), any());
        verify(notificationService).notify(eq(fulfillment.getOwner()), eq(fulfillment.getOrder()),
                eq(fulfillment), eq("WAREHOUSE_PICKUP_REJECTED"), any(), contains("No pickup capacity"));
    }

    @Test
    void adminCanReviewRecentDeliveryDecisionsWithOrderAndActorDetails() {
        User admin = User.builder().id(1L).fullName("Administrator")
                .email("admin@example.test").role(User.Role.ADMIN).build();
        DeliveryEvent decision = warehouseDecision("WAREHOUSE_PICKUP_ACCEPTED", originWarehouse);
        decision.setId(77L);
        decision.setActor(admin);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(eventRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(decision));

        var result = service.getRecentAdminDeliveryEvents(admin.getEmail());

        assertEquals(1, result.size());
        assertEquals(fulfillment.getOrder().getOrderNumber(), result.getFirst().getOrderNumber());
        assertEquals("Administrator", result.getFirst().getActorName());
        assertEquals("WAREHOUSE_PICKUP_ACCEPTED", result.getFirst().getEventType());
    }

    @Test
    void nonAdminCannotReviewGlobalDeliveryDecisionAudit() {
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));

        assertThrows(AccessDeniedException.class,
                () -> service.getRecentAdminDeliveryEvents(agent.getEmail()));
        verify(eventRepository, never()).findTop100ByOrderByCreatedAtDesc();
    }

    private DeliveryEvent warehouseDecision(String type, WarehouseLocation warehouse) {
        return DeliveryEvent.builder()
                .fulfillment(fulfillment)
                .actor(warehouse.getOperator())
                .eventType(type)
                .outcome("SUCCESS")
                .notes("warehouse:" + warehouse.getId() + ": recorded")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void originWarehouseOperatorCanSeeConfirmedOrdersAwaitingPickupAssignment() {
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository
                .findByWarehouseLocationIdOrDestinationWarehouseLocationIdOrderByCreatedAtAsc(
                        originWarehouse.getId(), originWarehouse.getId()))
                .thenReturn(List.of(fulfillment));

        var tasks = service.getWarehouseTasks(originWarehouseOperator.getEmail());

        assertEquals(1, tasks.size());
        assertEquals(fulfillment.getId(), tasks.getFirst().getId());
    }

    @Test
    void warehouseOperatorCanRetrievePersistentArrivalQr() {
        when(userRepository.findByEmail(originWarehouseOperator.getEmail()))
                .thenReturn(Optional.of(originWarehouseOperator));
        when(warehouseLocationRepository.findByOperatorId(originWarehouseOperator.getId()))
                .thenReturn(Optional.of(originWarehouse));
        when(secureTokenService.newQrToken()).thenReturn("warehouse-secret");
        when(secureTokenService.hashToken("9TAILS-WH:warehouse-secret")).thenReturn("warehouse-hash");
        when(secureTokenService.encrypt("9TAILS-WH:warehouse-secret")).thenReturn("ciphertext");
        when(secureTokenService.decrypt("ciphertext")).thenReturn("9TAILS-WH:warehouse-secret");
        when(warehouseLocationRepository.save(originWarehouse)).thenReturn(originWarehouse);

        var qr = service.getWarehouseArrivalQr(originWarehouseOperator.getEmail());

        assertEquals("Origin Hub", qr.getWarehouseName());
        assertEquals("9TAILS-WH:warehouse-secret", qr.getToken());
        assertEquals("warehouse-hash", originWarehouse.getArrivalQrTokenHash());
    }

    @Test
    void warehouseOperatorGetsReplacementQrWhenSavedTokenCannotBeDecrypted() {
        when(userRepository.findByEmail(originWarehouseOperator.getEmail()))
                .thenReturn(Optional.of(originWarehouseOperator));
        when(warehouseLocationRepository.findByOperatorId(originWarehouseOperator.getId()))
                .thenReturn(Optional.of(originWarehouse));
        originWarehouse.setArrivalQrTokenHash("old-hash");
        originWarehouse.setArrivalQrTokenCiphertext("old-ciphertext");
        when(secureTokenService.decrypt("old-ciphertext"))
                .thenThrow(new IllegalStateException("Unable to decrypt delivery token"));
        when(secureTokenService.newQrToken()).thenReturn("replacement-secret");
        when(secureTokenService.hashToken("9TAILS-WH:replacement-secret"))
                .thenReturn("replacement-hash");
        when(secureTokenService.encrypt("9TAILS-WH:replacement-secret"))
                .thenReturn("replacement-ciphertext");
        when(warehouseLocationRepository.save(originWarehouse)).thenReturn(originWarehouse);

        var qr = service.getWarehouseArrivalQr(originWarehouseOperator.getEmail());

        assertEquals("9TAILS-WH:replacement-secret", qr.getToken());
        assertTrue(qr.isRefreshed());
        assertEquals("replacement-hash", originWarehouse.getArrivalQrTokenHash());
        assertEquals("replacement-ciphertext", originWarehouse.getArrivalQrTokenCiphertext());
    }

    @Test
    void assignedAgentSeesOnlyPackagesWaitingAtTheScannedWarehouse() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER);
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(secureTokenService.hashToken("9TAILS-WH:warehouse-secret")).thenReturn("warehouse-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue("warehouse-hash"))
                .thenReturn(Optional.of(originWarehouse));
        when(fulfillmentRepository.findByDeliveryAgentIdOrderByCreatedAtDesc(agent.getId()))
                .thenReturn(List.of(fulfillment));

        var tasks = service.getWarehouseArrivalTasks(
                agent.getEmail(), "9TAILS-WH:warehouse-secret");

        assertEquals(1, tasks.size());
        assertEquals(fulfillment.getId(), tasks.getFirst().getId());
    }

    @Test
    void assignedAgentCanConfirmOriginWarehouseArrivalUsingWarehouseQr() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER);
        var request = new com.nintails.ecommerce.dto.WarehouseArrivalRequest();
        request.setToken("9TAILS-WH:warehouse-secret");
        request.setFulfillmentId(fulfillment.getId());
        request.setNotes("Arrived at origin hub");
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(secureTokenService.hashToken(request.getToken())).thenReturn("warehouse-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue("warehouse-hash"))
                .thenReturn(Optional.of(originWarehouse));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(fulfillmentRepository.findAllByOrderId(fulfillment.getOrder().getId()))
                .thenReturn(List.of(fulfillment));

        var result = service.confirmWarehouseArrival(agent.getEmail(), request);

        assertEquals(SellerFulfillment.FulfillmentStatus.PACKED_READY, fulfillment.getStatus());
        assertNotNull(fulfillment.getWarehouseReceivedAt());
        assertEquals("OUT_FOR_DELIVERY_TO_STORE", result.getNextStage());
        verify(eventRepository).save(argThat(event ->
                "WAREHOUSE_RECEIVED".equals(event.getEventType())
                        && "Arrived at origin hub".equals(event.getNotes())));
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(),
                eq(fulfillment), eq("WAREHOUSE_RECEIVED"), any(), any());
    }

    @Test
    void originArrivalSkipsTransferWhenOriginIsAlsoTheDestinationWarehouse() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER);
        fulfillment.setDestinationWarehouseLocation(originWarehouse);
        finalAgent.setDeliveryWarehouse(originWarehouse);
        var request = new com.nintails.ecommerce.dto.WarehouseArrivalRequest();
        request.setToken("9TAILS-WH:warehouse-secret");
        request.setFulfillmentId(fulfillment.getId());
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(secureTokenService.hashToken(request.getToken())).thenReturn("warehouse-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue("warehouse-hash"))
                .thenReturn(Optional.of(originWarehouse));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(fulfillmentRepository.findAllByOrderId(fulfillment.getOrder().getId()))
                .thenReturn(List.of(fulfillment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        fulfillment.setFinalDeliveryAgent(null);

        var result = service.confirmWarehouseArrival(agent.getEmail(), request);

        assertEquals(SellerFulfillment.FulfillmentStatus.AT_DESTINATION_STORE, fulfillment.getStatus());
        assertNull(fulfillment.getFinalDeliveryAgent());
        assertEquals("OUT_FOR_DELIVERY", result.getNextStage());
        verify(notificationService).notify(eq(fulfillment.getOrder().getUser()), any(),
                eq(fulfillment), eq("AT_DESTINATION_STORE"), any(), any());
        verify(notificationService).notify(eq(originWarehouseOperator), eq(fulfillment.getOrder()),
                eq(fulfillment), eq("WAREHOUSE_HANDOFF_READY"), any(), any());

        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(userRepository.findByIdAndRole(finalAgent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(finalAgent));
        service.assignFinalDeliveryAgent(originWarehouseOperator.getEmail(),
                fulfillment.getId(), finalAgent.getId());
        assertEquals(finalAgent, fulfillment.getFinalDeliveryAgent());
        assertNotNull(fulfillment.getFinalAgentOfferExpiresAt());
    }

    @Test
    void legacySingleWarehousePackedOrderSkipsTheInterWarehouseDispatchStage() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PACKED_READY);
        fulfillment.setDestinationWarehouseLocation(originWarehouse);
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(tokenRepository.findByTokenHashForUpdate("qr-hash")).thenReturn(Optional.of(token));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");
        fulfillment.setFinalDeliveryAgent(null);
        when(fulfillmentRepository.findAllByOrderId(fulfillment.getOrder().getId()))
                .thenReturn(List.of(fulfillment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        com.nintails.ecommerce.dto.StageSubmissionRequest request =
                new com.nintails.ecommerce.dto.StageSubmissionRequest();
        request.setToken("raw-token");

        var result = service.submitStage(originWarehouseOperator.getEmail(), "raw-token", request,
                Stage.STORE_RECEIVED);

        assertEquals(SellerFulfillment.FulfillmentStatus.AT_DESTINATION_STORE, fulfillment.getStatus());
        assertEquals("OUT_FOR_DELIVERY", result.getNextStage());
        assertNull(fulfillment.getFinalDeliveryAgent());
        verify(notificationService).notify(eq(originWarehouseOperator), eq(fulfillment.getOrder()),
                eq(fulfillment), eq("WAREHOUSE_HANDOFF_READY"), any(), any());
    }

    @Test
    void finalDeliveryAgentMustBeDifferentFromPickupAgentAtSingleWarehouse() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.AT_DESTINATION_STORE);
        fulfillment.setDestinationWarehouseLocation(originWarehouse);
        fulfillment.setFinalDeliveryAgent(null);
        fulfillment.setFinalAgentAcceptedAt(null);
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));
        when(userRepository.findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(agent));

        assertThrows(ConflictException.class, () -> service.assignFinalDeliveryAgent(
                originWarehouseOperator.getEmail(), fulfillment.getId(), agent.getId()));
        assertNull(fulfillment.getFinalDeliveryAgent());
    }

    @Test
    void warehouseQrCannotAdvanceAnOrderAssignedToAnotherWarehouse() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER);
        var request = new com.nintails.ecommerce.dto.WarehouseArrivalRequest();
        request.setToken("9TAILS-WH:destination-qr");
        request.setFulfillmentId(fulfillment.getId());
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(secureTokenService.hashToken(request.getToken())).thenReturn("destination-warehouse-qr-hash");
        when(warehouseLocationRepository.findByArrivalQrTokenHashAndActiveTrue(
                "destination-warehouse-qr-hash")).thenReturn(Optional.of(destinationWarehouse));
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));

        assertThrows(AccessDeniedException.class,
                () -> service.confirmWarehouseArrival(agent.getEmail(), request));
        assertEquals(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER,
                fulfillment.getStatus());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void differentWarehouseOperatorCannotChoosePickupAgent() {
        fulfillment.setDeliveryAgentAcceptedAt(null);
        stubWarehouseOperator(destinationWarehouseOperator, destinationWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));

        assertThrows(AccessDeniedException.class, () -> service.assignDeliveryAgent(
                destinationWarehouseOperator.getEmail(), fulfillment.getId(), agent.getId()));
        verify(userRepository, never()).findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT);
    }

    @Test
    void acceptedPickupRouteCannotBeReassigned() {
        stubWarehouseOperator(originWarehouseOperator, originWarehouse);
        when(fulfillmentRepository.findByIdForUpdate(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));

        assertThrows(ConflictException.class, () -> service.assignDeliveryAgent(
                originWarehouseOperator.getEmail(), fulfillment.getId(), agent.getId()));
        verify(userRepository, never()).findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT);
    }

    @Test
    void originalPackageQrCannotConfirmWarehouseArrival() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.PICKED_UP_FROM_OWNER);
        token.setUsedAt(LocalDateTime.now());
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");
        when(tokenRepository.findByTokenHash("qr-hash")).thenReturn(Optional.of(token));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.validateQr(agent.getEmail(), "raw-token"));
        assertTrue(exception.getMessage().contains("warehouse arrival QR"));
    }

    @Test
    void warehouseCannotReadHistoryForFulfillmentOutsideItsQueue() {
        User warehouse = User.builder().id(27L).email("warehouse@example.test")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        when(userRepository.findByEmail(warehouse.getEmail())).thenReturn(Optional.of(warehouse));
        when(fulfillmentRepository.findById(fulfillment.getId()))
                .thenReturn(Optional.of(fulfillment));

        assertThrows(AccessDeniedException.class, () ->
                service.getHistory(warehouse.getEmail(), fulfillment.getId()));
        verify(eventRepository, never()).findByFulfillmentIdOrderByCreatedAtAsc(any());
    }

    @Test
    void finalDeliveryAgentQrCompletesDeliveryAndNotifiesCustomerOwnerAndWarehouse() {
        fulfillment.setStatus(SellerFulfillment.FulfillmentStatus.OUT_FOR_DELIVERY);
        fulfillment.getOrder().setStatus(Order.OrderStatus.OUT_FOR_DELIVERY);
        DeliveryStageToken finalToken = DeliveryStageToken.builder().id(51L)
                .fulfillment(fulfillment).stage(Stage.PICKUP_FROM_OWNER).tokenHash("qr-hash")
                .tokenCiphertext("encrypted")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        token = finalToken;
        stubAgentAndLockedFulfillment(finalAgent);
        when(tokenRepository.findByTokenHashForUpdate("qr-hash")).thenReturn(Optional.of(finalToken));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");
        when(eventRepository.save(any(DeliveryEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fulfillmentRepository.findAllByOrderId(fulfillment.getOrder().getId()))
                .thenReturn(List.of(fulfillment));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCustomerDeliveryConfirmation(finalAgent.getEmail(), "raw-token", "Customer present");

        assertEquals(SellerFulfillment.FulfillmentStatus.DELIVERED,
                fulfillment.getStatus());
        assertNotNull(finalToken.getUsedAt());
        verify(notificationService).notify(eq(customer), any(), eq(fulfillment),
                eq("DELIVERY_CONFIRMED"), any(), contains("Congratulations"));
        assertThrows(ConflictException.class, () ->
                service.requestCustomerDeliveryConfirmation(
                        finalAgent.getEmail(), "raw-token", "Repeated QR scan"));
        verify(notificationService, times(1)).notify(eq(customer), any(), eq(fulfillment),
                eq("DELIVERY_CONFIRMED"), any(), contains("Congratulations"));

        assertNotNull(fulfillment.getDeliveredAt());
        assertEquals(Order.OrderStatus.DELIVERED, fulfillment.getOrder().getStatus());
        assertNotNull(finalToken.getUsedAt());
        verify(notificationService).notify(eq(customer), any(), eq(fulfillment),
                eq("DELIVERY_CONFIRMED"), any(), contains("Congratulations"));
        verify(notificationService).notify(eq(fulfillment.getOwner()), any(), eq(fulfillment),
                eq("DELIVERY_COMPLETED"), any(), any());
        verify(notificationService).notify(eq(finalAgent), any(), eq(fulfillment),
                eq("DELIVERY_COMPLETED"), any(), any());
        verify(notificationService).notify(eq(destinationWarehouseOperator), any(), eq(fulfillment),
                eq("DELIVERY_COMPLETED"), any(), any());
        verify(eventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void unassignedAgentCannotUsePickupToken() {
        User stranger = User.builder().id(99L).email("stranger@example.test")
                .role(User.Role.DELIVERY_AGENT).build();
        when(userRepository.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(secureTokenService.hashToken("raw-token")).thenReturn("qr-hash");
        when(tokenRepository.findByTokenHash("qr-hash")).thenReturn(Optional.of(token));

        assertThrows(AccessDeniedException.class, () -> service.validateQr(stranger.getEmail(), "raw-token"));
        assertNull(token.getUsedAt());
        verify(tokenRepository, never()).findByTokenHashForUpdate("qr-hash");
    }
}
