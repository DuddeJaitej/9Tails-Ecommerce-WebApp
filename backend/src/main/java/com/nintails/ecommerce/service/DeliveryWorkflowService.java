package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.*;
import com.nintails.ecommerce.entity.*;
import com.nintails.ecommerce.entity.DeliveryStageToken.Stage;
import com.nintails.ecommerce.entity.Order.OrderStatus;
import com.nintails.ecommerce.entity.SellerFulfillment.FulfillmentStatus;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.exception.ExpiredResourceException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.*;
import com.nintails.ecommerce.security.SecureTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeliveryWorkflowService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SellerFulfillmentRepository fulfillmentRepository;
    private final DeliveryStageTokenRepository tokenRepository;
    private final DeliveryEventRepository eventRepository;
    private final BusinessLocationRepository businessLocationRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final NotificationService notificationService;
    private final SecureTokenService secureTokenService;
    private final ServiceRequestService serviceRequestService;

    @Value("${app.delivery.qr-expiry-hours:168}")
    private long qrExpiryHours;

    @Transactional(readOnly = true)
    public List<FulfillmentDto> getOwnerTasks(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.OWNER, User.Role.ADMIN);
        List<SellerFulfillment> tasks;
        if (actor.getRole() == User.Role.ADMIN) {
            tasks = fulfillmentRepository.findAll();
        } else {
            java.util.Map<Long, SellerFulfillment> byId = new java.util.LinkedHashMap<>();
            fulfillmentRepository.findByOwnerIdOrderByCreatedAtDesc(actor.getId())
                    .forEach(task -> byId.put(task.getId(), task));
            fulfillmentRepository.findByDestinationStore_Owner_IdOrderByCreatedAtDesc(actor.getId())
                    .forEach(task -> byId.put(task.getId(), task));
            tasks = List.copyOf(byId.values());
        }
        return tasks.stream().map(task -> toDto(task, false)
                .withServiceRequests(serviceRequestService.forFulfillment(task.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<FulfillmentDto> getDeliveryTasks(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.DELIVERY_AGENT, User.Role.ADMIN);
        if (actor.getRole() == User.Role.DELIVERY_AGENT
                && (!Boolean.TRUE.equals(actor.getDeliveryAgentActive())
                || !isWarehouseActive(actor.getDeliveryWarehouse()))) {
            throw new AccessDeniedException("Your warehouse operator must activate your account before you can receive delivery tasks.");
        }
        List<SellerFulfillment> tasks;
        if (actor.getRole() == User.Role.ADMIN) {
            tasks = fulfillmentRepository.findAll();
        } else {
            java.util.Map<Long, SellerFulfillment> byId = new java.util.LinkedHashMap<>();
            fulfillmentRepository.findByDeliveryAgentIdOrderByCreatedAtDesc(actor.getId())
                    .forEach(task -> byId.put(task.getId(), task));
            fulfillmentRepository.findByFinalDeliveryAgentIdOrderByCreatedAtDesc(actor.getId())
                    .forEach(task -> byId.put(task.getId(), task));
            fulfillmentRepository.findByTransferDeliveryAgentIdOrderByCreatedAtDesc(actor.getId())
                    .forEach(task -> byId.put(task.getId(), task));
            tasks = List.copyOf(byId.values());
        }
        return tasks.stream().sorted(Comparator.comparing(SellerFulfillment::getCreatedAt))
                .map(task -> toDto(task, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<FulfillmentDto> getWarehouseTasks(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN);
        List<SellerFulfillment> tasks;
        if (actor.getRole() == User.Role.ADMIN) {
            tasks = fulfillmentRepository.findAll();
        } else {
            WarehouseLocation warehouse = warehouseLocationRepository.findByOperatorId(actor.getId())
                    .filter(location -> Boolean.TRUE.equals(location.getActive()))
                    .orElseThrow(() -> new AccessDeniedException(
                            "Your warehouse must be approved by an administrator before operations begin."));
            tasks = fulfillmentRepository.findByWarehouseLocationIdOrDestinationWarehouseLocationIdOrderByCreatedAtAsc(
                    warehouse.getId(), warehouse.getId()).stream()
                    .filter(task -> {
                        boolean originTask = task.getWarehouseLocation() != null
                                && task.getWarehouseLocation().getId().equals(warehouse.getId())
                                && List.of(FulfillmentStatus.CONFIRMED,
                                        FulfillmentStatus.PICKED_UP_FROM_OWNER,
                                        FulfillmentStatus.PACKED_READY)
                                .contains(task.getStatus());
                        boolean destinationTask = task.getDestinationWarehouseLocation() != null
                                && task.getDestinationWarehouseLocation().getId().equals(warehouse.getId())
                                && List.of(FulfillmentStatus.IN_TRANSIT_TO_STORE,
                                FulfillmentStatus.AT_DESTINATION_STORE)
                                .contains(task.getStatus());
                        return originTask || destinationTask;
                    })
                    .toList();
        }
        return tasks.stream().map(this::toWarehouseDto).toList();
    }

    @Transactional
    public WarehouseQrDto getWarehouseArrivalQr(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR);
        WarehouseLocation warehouse = warehouseLocationRepository.findByOperatorId(actor.getId())
                .filter(location -> Boolean.TRUE.equals(location.getActive()))
                .orElseThrow(() -> new AccessDeniedException(
                        "Your warehouse must be approved before its arrival QR is available."));
        boolean refreshed = false;
        if (warehouse.getArrivalQrTokenHash() == null
                || warehouse.getArrivalQrTokenCiphertext() == null) {
            String token = "9TAILS-WH:" + secureTokenService.newQrToken();
            warehouse.setArrivalQrTokenHash(secureTokenService.hashToken(token));
            warehouse.setArrivalQrTokenCiphertext(secureTokenService.encrypt(token));
            warehouse = warehouseLocationRepository.save(warehouse);
        }
        String token;
        try {
            token = secureTokenService.decrypt(warehouse.getArrivalQrTokenCiphertext());
        } catch (IllegalStateException exception) {
            if (!"Unable to decrypt delivery token".equals(exception.getMessage())) {
                throw exception;
            }
            token = "9TAILS-WH:" + secureTokenService.newQrToken();
            warehouse.setArrivalQrTokenHash(secureTokenService.hashToken(token));
            warehouse.setArrivalQrTokenCiphertext(secureTokenService.encrypt(token));
            warehouse = warehouseLocationRepository.save(warehouse);
            refreshed = true;
        }
        return WarehouseQrDto.builder()
                .warehouseName(warehouse.getName())
                .warehouseAddress(warehouse.getStreet() + ", " + warehouse.getCity()
                        + " " + warehouse.getPostalCode())
                .token(token)
                .refreshed(refreshed)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FulfillmentDto> getWarehouseArrivalTasks(String email, String rawToken) {
        User actor = getUser(email);
        requireRole(actor, User.Role.DELIVERY_AGENT);
        if (!Boolean.TRUE.equals(actor.getDeliveryAgentActive())
                || !isWarehouseActive(actor.getDeliveryWarehouse())) {
            throw new AccessDeniedException(
                    "Your warehouse operator must activate your account before you can receive delivery tasks.");
        }
        WarehouseLocation warehouse = getWarehouseFromArrivalQr(rawToken);
        java.util.Map<Long, SellerFulfillment> arrivals = new java.util.LinkedHashMap<>();
        fulfillmentRepository.findByDeliveryAgentIdOrderByCreatedAtDesc(actor.getId()).stream()
                .filter(fulfillment -> fulfillment.getStatus() == FulfillmentStatus.PICKED_UP_FROM_OWNER)
                .filter(fulfillment -> fulfillment.getWarehouseLocation() != null
                        && warehouse.getId().equals(fulfillment.getWarehouseLocation().getId()))
                .filter(fulfillment -> fulfillment.getDeliveryAgentAcceptedAt() != null
                        && fulfillment.getDeliveryAgentRouteDeadline() != null
                        && fulfillment.getDeliveryAgentRouteDeadline().isAfter(now()))
                .forEach(fulfillment -> arrivals.put(fulfillment.getId(), fulfillment));
        fulfillmentRepository.findByTransferDeliveryAgentIdOrderByCreatedAtDesc(actor.getId()).stream()
                .filter(fulfillment -> fulfillment.getStatus() == FulfillmentStatus.IN_TRANSIT_TO_STORE)
                .filter(fulfillment -> fulfillment.getDestinationWarehouseLocation() != null
                        && warehouse.getId().equals(fulfillment.getDestinationWarehouseLocation().getId()))
                .filter(fulfillment -> fulfillment.getTransferAgentAcceptedAt() != null
                        && fulfillment.getTransferAgentRouteDeadline() != null
                        && fulfillment.getTransferAgentRouteDeadline().isAfter(now()))
                .forEach(fulfillment -> arrivals.put(fulfillment.getId(), fulfillment));
        return arrivals.values().stream()
                .map(fulfillment -> toDto(fulfillment, false))
                .toList();
    }

    @Transactional
    public StageResultDto confirmWarehouseArrival(String email, WarehouseArrivalRequest request) {
        User actor = getUser(email);
        requireRole(actor, User.Role.DELIVERY_AGENT);
        WarehouseLocation warehouse = getWarehouseFromArrivalQr(request.getToken());
        SellerFulfillment fulfillment = getLockedFulfillment(request.getFulfillmentId());
        LocalDateTime at = now();
        FulfillmentStatus previous = fulfillment.getStatus();
        Stage nextStage;
        if (fulfillment.getStatus() == FulfillmentStatus.PICKED_UP_FROM_OWNER) {
            if (fulfillment.getWarehouseLocation() == null
                    || !warehouse.getId().equals(fulfillment.getWarehouseLocation().getId())) {
                throw new AccessDeniedException(
                        "This warehouse QR does not match the order's assigned origin warehouse.");
            }
            authorizeForStage(actor, fulfillment, Stage.WAREHOUSE_RECEIVED);
            validateStageStatus(fulfillment, Stage.WAREHOUSE_RECEIVED);
            fulfillment.setWarehouseReceivedAt(at);
            boolean alreadyAtDestination = isSingleWarehouseFulfillment(fulfillment);
            fulfillment.setStatus(FulfillmentStatus.PACKED_READY);
            record(fulfillment, actor, Stage.WAREHOUSE_RECEIVED.name(), previous,
                    FulfillmentStatus.PACKED_READY, "SUCCESS", null, request.getNotes());
            notifyStage(fulfillment, Stage.WAREHOUSE_RECEIVED);
            nextStage = alreadyAtDestination ? Stage.OUT_FOR_DELIVERY : Stage.OUT_FOR_DELIVERY_TO_STORE;
            if (alreadyAtDestination) {
                fulfillment.setStatus(FulfillmentStatus.AT_DESTINATION_STORE);
                record(fulfillment, actor, "AT_DESTINATION_STORE", FulfillmentStatus.PACKED_READY,
                        FulfillmentStatus.AT_DESTINATION_STORE, "SUCCESS", null,
                        "Origin and destination are the same warehouse.");
                notifyStage(fulfillment, Stage.STORE_RECEIVED);
                prepareFinalDelivery(fulfillment);
            }
        } else if (fulfillment.getStatus() == FulfillmentStatus.IN_TRANSIT_TO_STORE) {
            if (fulfillment.getDestinationWarehouseLocation() == null
                    || !warehouse.getId().equals(fulfillment.getDestinationWarehouseLocation().getId())) {
                throw new AccessDeniedException(
                        "This warehouse QR does not match the order's assigned destination warehouse.");
            }
            authorizeTransferAgent(actor, fulfillment);
            fulfillment.setStatus(FulfillmentStatus.AT_DESTINATION_STORE);
            record(fulfillment, actor, Stage.STORE_RECEIVED.name(), previous,
                    fulfillment.getStatus(), "SUCCESS", null, request.getNotes());
            nextStage = Stage.OUT_FOR_DELIVERY;
            notifyStage(fulfillment, Stage.STORE_RECEIVED);
            prepareFinalDelivery(fulfillment);
        } else {
            throw new ConflictException("This order is not waiting to arrive at this warehouse.");
        }
        refreshOrderStatus(fulfillment.getOrder());
        return StageResultDto.builder()
                .fulfillment(toDto(fulfillment, false))
                .nextStage(nextStage.name())
                .build();
    }

    private WarehouseLocation getWarehouseFromArrivalQr(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("9TAILS-WH:")) {
            throw new BadRequestException("Scan a valid warehouse arrival QR code.");
        }
        return warehouseLocationRepository
                .findByArrivalQrTokenHashAndActiveTrue(secureTokenService.hashToken(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Active warehouse arrival QR"));
    }

    @Transactional
    public FulfillmentDto acceptWarehousePickup(String email, Long fulfillmentId) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        if (fulfillment.getStatus() != FulfillmentStatus.CONFIRMED) {
            throw new ConflictException("Only a seller-confirmed order can be accepted by the pickup warehouse.");
        }
        authorizeWarehouseOperatorOrAdmin(actor, fulfillment, false);
        DeliveryEvent decision = warehousePickupDecision(fulfillment, fulfillment.getWarehouseLocation());
        if (decision != null) {
            if ("WAREHOUSE_PICKUP_ACCEPTED".equals(decision.getEventType())) {
                return toWarehouseDto(fulfillment);
            }
            throw new ConflictException("This warehouse has already declined this pickup request.");
        }
        record(fulfillment, actor, "WAREHOUSE_PICKUP_ACCEPTED", fulfillment.getStatus(),
                fulfillment.getStatus(), "SUCCESS", null,
                "warehouse:" + fulfillment.getWarehouseLocation().getId()
                        + ": Pickup accepted by " + fulfillment.getWarehouseLocation().getName() + ".");
        notificationService.notify(fulfillment.getOwner(), fulfillment.getOrder(), fulfillment,
                "WAREHOUSE_PICKUP_ACCEPTED", "Pickup warehouse accepted the order",
                fulfillment.getWarehouseLocation().getName() + " accepted pickup for order "
                        + fulfillment.getOrder().getOrderNumber()
                        + ". The warehouse will now assign an active delivery agent.");
        return toWarehouseDto(fulfillment);
    }

    @Transactional
    public FulfillmentDto rejectWarehousePickup(String email, Long fulfillmentId, String reason) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN);
        if (isBlank(reason) || reason.trim().length() > 1000) {
            throw new BadRequestException("Provide a pickup rejection reason of at most 1000 characters.");
        }
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        if (fulfillment.getStatus() != FulfillmentStatus.CONFIRMED) {
            throw new ConflictException("Only a seller-confirmed order can be declined by the pickup warehouse.");
        }
        authorizeWarehouseOperatorOrAdmin(actor, fulfillment, false);
        WarehouseLocation declinedWarehouse = fulfillment.getWarehouseLocation();
        if (warehousePickupDecision(fulfillment, declinedWarehouse) != null) {
            throw new ConflictException("A pickup decision has already been recorded for this warehouse.");
        }
        record(fulfillment, actor, "WAREHOUSE_PICKUP_REJECTED", fulfillment.getStatus(),
                fulfillment.getStatus(), "REJECTED", null,
                "warehouse:" + declinedWarehouse.getId() + ": Warehouse "
                        + declinedWarehouse.getName() + " declined pickup. Reason: " + reason.trim());

        Set<Long> declinedWarehouseIds = eventRepository
                .findByFulfillmentIdOrderByCreatedAtAsc(fulfillmentId).stream()
                .filter(event -> "WAREHOUSE_PICKUP_REJECTED".equals(event.getEventType()))
                .map(this::warehouseIdFromDecisionEvent)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        WarehouseLocation nextWarehouse = warehouseLocationRepository
                .findByActiveTrueOrderByNameAsc().stream()
                .filter(location -> location.getLatitude() != null && location.getLongitude() != null)
                .filter(location -> location.getOperator() != null)
                .filter(location -> !declinedWarehouseIds.contains(location.getId()))
                .min(Comparator.comparingDouble(location -> distanceKm(
                        fulfillment.getPickupLocation().getLatitude(),
                        fulfillment.getPickupLocation().getLongitude(),
                        location.getLatitude(), location.getLongitude())))
                .orElse(null);

        if (nextWarehouse != null) {
            fulfillment.setWarehouseLocation(nextWarehouse);
            fulfillment.setDeliveryAgent(null);
            fulfillment.setDeliveryAgentAcceptedAt(null);
            fulfillment.setDeliveryAgentOfferExpiresAt(null);
            fulfillment.setDeliveryAgentRouteDeadline(null);
            record(fulfillment, actor, "WAREHOUSE_PICKUP_REROUTED", fulfillment.getStatus(),
                    fulfillment.getStatus(), "SUCCESS", null,
                    "Pickup request rerouted to " + nextWarehouse.getName()
                            + " after " + declinedWarehouse.getName() + " declined it.");
            notificationService.notify(nextWarehouse.getOperator(), fulfillment.getOrder(), fulfillment,
                    "PICKUP_AGENT_ASSIGNMENT_REQUIRED", "Pickup request assigned to your warehouse",
                    "Order " + fulfillment.getOrder().getOrderNumber() + " needs pickup from "
                            + fulfillment.getPickupStreet() + ", " + fulfillment.getPickupCity() + " "
                            + fulfillment.getPickupPostalCode()
                            + ". Review the route map and accept or decline the pickup request.");
        } else {
            notificationService.notifyAdmins(
                    userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN),
                    fulfillment.getOrder(), fulfillment, "WAREHOUSE_PICKUP_UNAVAILABLE",
                    "No warehouse accepted pickup",
                    "Order " + fulfillment.getOrder().getOrderNumber()
                            + " was declined by " + declinedWarehouse.getName()
                            + ". No other active, staffed warehouse is available. Reason: " + reason.trim());
        }
        notificationService.notify(fulfillment.getOwner(), fulfillment.getOrder(), fulfillment,
                "WAREHOUSE_PICKUP_REJECTED", "Pickup warehouse declined the order",
                declinedWarehouse.getName() + " declined pickup for order "
                        + fulfillment.getOrder().getOrderNumber() + ". Reason: " + reason.trim()
                        + (nextWarehouse == null ? " An administrator has been notified."
                        : " The request was routed to " + nextWarehouse.getName() + "."));
        return toWarehouseDto(fulfillment);
    }

    @Transactional(readOnly = true)
    public List<DeliveryEventDto> getRecentAdminDeliveryEvents(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.ADMIN);
        return eventRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toDeliveryEventDto)
                .toList();
    }

    private FulfillmentDto toWarehouseDto(SellerFulfillment fulfillment) {
        FulfillmentDto dto = toDto(fulfillment, false);
        DeliveryEvent decision = warehousePickupDecision(
                fulfillment, fulfillment.getWarehouseLocation());
        return dto.withWarehousePickupDecision(decision == null ? "PENDING"
                        : "WAREHOUSE_PICKUP_ACCEPTED".equals(decision.getEventType())
                                ? "ACCEPTED" : "REJECTED")
                .withWarehousePickupDecisionNotes(decision == null ? null : decision.getNotes())
                .withWarehousePickupDecisionBy(decision == null || decision.getActor() == null
                        ? null : decision.getActor().getFullName())
                .withWarehousePickupDecisionAt(decision == null ? null : decision.getCreatedAt())
                .withCustomerPhone(fulfillment.getOrder().getUser().getPhoneNumber());
    }

    private DeliveryEvent warehousePickupDecision(
            SellerFulfillment fulfillment, WarehouseLocation warehouse) {
        if (warehouse == null) return null;
        String decisionPrefix = "warehouse:" + warehouse.getId() + ":";
        return eventRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillment.getId()).stream()
                .filter(event -> ("WAREHOUSE_PICKUP_ACCEPTED".equals(event.getEventType())
                                || "WAREHOUSE_PICKUP_REJECTED".equals(event.getEventType()))
                        && event.getNotes() != null
                        && event.getNotes().startsWith(decisionPrefix))
                .reduce((previous, latest) -> latest)
                .orElse(null);
    }

    private Long warehouseIdFromDecisionEvent(DeliveryEvent event) {
        if (event.getNotes() == null || !event.getNotes().startsWith("warehouse:")) return null;
        int separator = event.getNotes().indexOf(':', "warehouse:".length());
        if (separator < 0) return null;
        try {
            return Long.parseLong(event.getNotes().substring("warehouse:".length(), separator));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private DeliveryEventDto toDeliveryEventDto(DeliveryEvent event) {
        return DeliveryEventDto.builder()
                .id(event.getId())
                .fulfillmentId(event.getFulfillment().getId())
                .orderNumber(event.getFulfillment().getOrder().getOrderNumber())
                .eventType(event.getEventType())
                .previousStatus(event.getPreviousStatus())
                .newStatus(event.getNewStatus())
                .outcome(event.getOutcome())
                .packageCondition(event.getPackageCondition())
                .notes(event.getNotes())
                .actorName(event.getActor() == null ? "System" : event.getActor().getFullName())
                .createdAt(event.getCreatedAt())
                .build();
    }

    @Transactional
    public FulfillmentDto setPickupLocation(String email, Long fulfillmentId, Long businessLocationId) {
        User actor = getUser(email);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        authorizeOwnerOrAdmin(actor, fulfillment);
        if (fulfillment.getStatus() != FulfillmentStatus.AWAITING_OWNER_CONFIRMATION) {
            throw new ConflictException("The pickup location can only be changed before order confirmation.");
        }
        if (fulfillment.getOwner() == null) {
            throw new ConflictException("This fulfillment does not have a business owner pickup location.");
        }
        BusinessLocation location = businessLocationRepository
                .findByIdAndOwnerId(businessLocationId, fulfillment.getOwner().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Business pickup location", businessLocationId));
        if (!Boolean.TRUE.equals(location.getVerifiedByOwner())
                || location.getLatitude() == null
                || location.getLongitude() == null
                || isBlank(location.getStreet())
                || isBlank(location.getPostalCode())
                || location.getLatitude() < -90 || location.getLatitude() > 90
                || location.getLongitude() < -180 || location.getLongitude() > 180) {
            throw new ConflictException("Select a verified pickup location with a complete address and map coordinates.");
        }
        fulfillment.setPickupLocation(location);
        fulfillment.setPickupName(location.getName());
        fulfillment.setPickupStreet(location.getStreet());
        fulfillment.setPickupCity(location.getCity());
        fulfillment.setPickupState(location.getState());
        fulfillment.setPickupPostalCode(location.getPostalCode());
        fulfillment.setPickupCountry(location.getCountry());
        return toDto(fulfillmentRepository.save(fulfillment), false);
    }

    @Transactional
    public StageResultDto confirmOrder(String email, Long fulfillmentId) {
        User actor = getUser(email);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        authorizeOwnerOrAdmin(actor, fulfillment);
        if (fulfillment.getStatus() != FulfillmentStatus.AWAITING_OWNER_CONFIRMATION) {
            throw new ConflictException("This seller fulfillment has already received a response.");
        }
        if (fulfillment.getOrder().getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("A cancelled order cannot be confirmed.");
        }
        if (fulfillment.getOrder().getPaymentMethod() == Order.PaymentMethod.UPI
                && fulfillment.getOrder().getPaymentStatus() != Order.PaymentStatus.PAID) {
            throw new ConflictException("The online payment must be verified before order confirmation.");
        }
        if (isBlank(fulfillment.getPickupStreet())
                || isBlank(fulfillment.getPickupPostalCode())
                || fulfillment.getPickupLocation() == null
                || fulfillment.getPickupLocation().getLatitude() == null
                || fulfillment.getPickupLocation().getLongitude() == null
                || fulfillment.getPickupLocation().getLatitude() < -90
                || fulfillment.getPickupLocation().getLatitude() > 90
                || fulfillment.getPickupLocation().getLongitude() < -180
                || fulfillment.getPickupLocation().getLongitude() > 180) {
            throw new ConflictException(
                    "A business pickup location with map coordinates is required before confirming this order.");
        }
        if (fulfillment.getOrder().getAddressLatitude() == null
                || fulfillment.getOrder().getAddressLongitude() == null) {
            throw new ConflictException(
                    "Add the delivery address location on the map before confirming this order.");
        }
        List<WarehouseLocation> warehouses = warehouseLocationRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(location -> location.getLatitude() != null && location.getLongitude() != null)
                .toList();
        WarehouseLocation warehouse = warehouses.stream()
                .min(Comparator.comparingDouble(location -> distanceKm(
                        fulfillment.getPickupLocation().getLatitude(),
                        fulfillment.getPickupLocation().getLongitude(),
                        location.getLatitude(), location.getLongitude())))
                .orElseThrow(() -> new ConflictException(
                        "An administrator must approve a warehouse with map coordinates."));
        WarehouseLocation destinationWarehouse = warehouses.stream()
                .min(Comparator.comparingDouble(location -> distanceKm(
                        fulfillment.getOrder().getAddressLatitude(),
                        fulfillment.getOrder().getAddressLongitude(),
                        location.getLatitude(), location.getLongitude())))
                .orElseThrow(() -> new ConflictException("No approved destination warehouse is available."));

        LocalDateTime now = now();
        fulfillment.setWarehouseLocation(warehouse);
        fulfillment.setDestinationWarehouseLocation(destinationWarehouse);
        fulfillment.setDestinationStore(null);
        fulfillment.setStatus(FulfillmentStatus.CONFIRMED);
        fulfillment.setOwnerRespondedAt(now);
        fulfillment.setExpectedDeliveryDate(LocalDate.now(BUSINESS_ZONE)
                .plusDays(5).atTime(now.toLocalTime()));
        DeliveryStageToken pickupToken = issueToken(fulfillment, Stage.PICKUP_FROM_OWNER);
        record(fulfillment, actor, "OWNER_CONFIRMED", FulfillmentStatus.AWAITING_OWNER_CONFIRMATION,
                fulfillment.getStatus(), "SUCCESS", null, null);
        refreshOrderStatus(fulfillment.getOrder());
        notificationService.notify(fulfillment.getOrder().getUser(), fulfillment.getOrder(),
                fulfillment, "SELLER_CONFIRMED", "Seller confirmed your order",
                "The seller confirmed order " + fulfillment.getOrder().getOrderNumber() + ".");
        if (warehouse.getOperator() != null) {
            notificationService.notify(warehouse.getOperator(), fulfillment.getOrder(), fulfillment,
                    "PICKUP_AGENT_ASSIGNMENT_REQUIRED", "Order ready for pickup assignment",
                    "Choose an active delivery agent to collect order "
                            + fulfillment.getOrder().getOrderNumber() + " from the seller at "
                            + fulfillment.getPickupStreet() + ", "
                            + fulfillment.getPickupCity() + " "
                            + fulfillment.getPickupPostalCode() + ". Your warehouse task includes "
                            + "the saved map locations and route.");
        }
        return StageResultDto.builder()
                .fulfillment(toDto(fulfillment, false))
                .nextStage(Stage.PICKUP_FROM_OWNER.name())
                .nextQrToken(secureTokenService.decrypt(pickupToken.getTokenCiphertext()))
                .build();
    }

    @Transactional
    public FulfillmentDto rejectOrder(String email, Long fulfillmentId, String reason) {
        User actor = getUser(email);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        authorizeOwnerOrAdmin(actor, fulfillment);
        if (fulfillment.getStatus() != FulfillmentStatus.AWAITING_OWNER_CONFIRMATION) {
            throw new ConflictException("Only a fulfillment awaiting confirmation can be rejected.");
        }
        FulfillmentStatus previous = fulfillment.getStatus();
        fulfillment.setStatus(FulfillmentStatus.REJECTED);
        fulfillment.setRejectionReason(reason.trim());
        fulfillment.setOwnerRespondedAt(now());
        revokeUnusedTokens(fulfillment);

        for (OrderItem item : fulfillment.getItems()) {
            if (!item.isCancelled() && item.isStockReserved()) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", item.getProduct().getId()));
                product.setStock(product.getStock() + item.getQuantity());
                item.setCancelled(true);
                item.setStockReserved(false);
            }
        }
        Order order = fulfillment.getOrder();
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            order.setPaymentStatus(Order.PaymentStatus.REFUND_PENDING);
            userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN).forEach(admin ->
                    notificationService.notify(admin, order, fulfillment, "REFUND_REQUIRES_REVIEW",
                            "Order rejection needs refund review",
                            "A paid seller fulfillment was rejected; review the payment for order "
                                    + order.getOrderNumber() + "."));
        } else {
            recalculateUnpaidOrder(order);
        }
        record(fulfillment, actor, "OWNER_REJECTED", previous, fulfillment.getStatus(),
                "SUCCESS", null, reason.trim());
        refreshOrderStatus(order);
        notificationService.notify(order.getUser(), order, fulfillment, "SELLER_REJECTED",
                "Seller could not fulfill an item",
                "The seller could not fulfill part of order " + order.getOrderNumber()
                        + ". Reason: " + reason.trim()
                        + (order.getPaymentStatus() == Order.PaymentStatus.REFUND_PENDING
                        ? " A refund review is required." : ""));
        return toDto(fulfillment, false);
    }

    @Transactional
    public FulfillmentDto assignDeliveryAgent(String email, Long fulfillmentId, Long agentId) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        if (fulfillment.getStatus() != FulfillmentStatus.CONFIRMED) {
            throw new ConflictException("A first-leg agent can be assigned after seller confirmation.");
        }
        authorizeWarehouseOperatorOrAdmin(actor, fulfillment, false);
        DeliveryEvent decision = warehousePickupDecision(fulfillment, fulfillment.getWarehouseLocation());
        if (decision == null || !"WAREHOUSE_PICKUP_ACCEPTED".equals(decision.getEventType())) {
            throw new ConflictException("The pickup warehouse must accept this order before assigning an agent.");
        }
        if (fulfillment.getDeliveryAgentAcceptedAt() != null) {
            throw new ConflictException("The assigned delivery agent has already accepted this route.");
        }
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent", agentId));
        if (!Boolean.TRUE.equals(agent.getDeliveryAgentActive())
                || agent.getDeliveryWarehouse() == null
                || !isWarehouseActive(agent.getDeliveryWarehouse())
                || fulfillment.getWarehouseLocation() == null
                || !agent.getDeliveryWarehouse().getId().equals(
                        fulfillment.getWarehouseLocation().getId())) {
            throw new ConflictException(
                    "Choose an active delivery agent registered under the origin warehouse.");
        }
        offerAgent(fulfillment, agent, false);
        return toDto(fulfillment, true);
    }

    @Transactional
    public FulfillmentDto assignTransferDeliveryAgent(String email, Long fulfillmentId, Long agentId) {
        User actor = getUser(email);
        requireRole(actor, User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        if (fulfillment.getStatus() != FulfillmentStatus.PACKED_READY
                || isSingleWarehouseFulfillment(fulfillment)) {
            throw new ConflictException(
                    "A transfer agent can be assigned only when a package at its origin warehouse needs to travel to a different destination warehouse.");
        }
        if (fulfillment.getTransferAgentAcceptedAt() != null) {
            throw new ConflictException(
                    "The transfer agent has accepted this route and cannot be replaced.");
        }
        authorizeWarehouseOperatorOrAdmin(actor, fulfillment, false);
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent", agentId));
        if (!Boolean.TRUE.equals(agent.getDeliveryAgentActive())
                || agent.getDeliveryWarehouse() == null
                || !isWarehouseActive(agent.getDeliveryWarehouse())
                || fulfillment.getWarehouseLocation() == null
                || !agent.getDeliveryWarehouse().getId().equals(
                        fulfillment.getWarehouseLocation().getId())) {
            throw new ConflictException(
                    "Choose an active delivery agent registered under the pickup warehouse.");
        }
        if (fulfillment.getDeliveryAgent() != null
                && fulfillment.getDeliveryAgent().getId().equals(agent.getId())) {
            throw new ConflictException(
                    "Choose a different delivery agent for the warehouse-to-warehouse transfer.");
        }
        offerTransferAgent(fulfillment, agent);
        return toDto(fulfillment, true);
    }

    @Transactional
    public FulfillmentDto assignFinalDeliveryAgent(String email, Long fulfillmentId, Long agentId) {
        User actor = getUser(email);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        authorizeWarehouseOperatorOrAdmin(actor, fulfillment, true);
        if (fulfillment.getStatus() != FulfillmentStatus.AT_DESTINATION_STORE
                || fulfillment.getDestinationWarehouseLocation() == null) {
            throw new ConflictException(
                    "A final delivery agent can be assigned after the package reaches the destination warehouse.");
        }
        if (fulfillment.getFinalAgentAcceptedAt() != null) {
            throw new ConflictException(
                    "The final-delivery agent has accepted this route and cannot be replaced.");
        }
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent", agentId));
        if (!Boolean.TRUE.equals(agent.getDeliveryAgentActive())
                || agent.getDeliveryWarehouse() == null
                || !isWarehouseActive(agent.getDeliveryWarehouse())
                || !agent.getDeliveryWarehouse().getId().equals(
                        fulfillment.getDestinationWarehouseLocation().getId())) {
            throw new ConflictException(
                    "Choose an active delivery agent registered under the destination warehouse.");
        }
        if (fulfillment.getDeliveryAgent() != null
                && fulfillment.getDeliveryAgent().getId().equals(agent.getId())
                || fulfillment.getTransferDeliveryAgent() != null
                && fulfillment.getTransferDeliveryAgent().getId().equals(agent.getId())) {
            throw new ConflictException(
                    "Choose a different agent from the one who handled pickup or warehouse transfer.");
        }
        offerAgent(fulfillment, agent, true);
        return toDto(fulfillment, true);
    }

    @Transactional
    public FulfillmentDto acceptDeliveryOffer(String email, Long fulfillmentId) {
        User agent = getUser(email);
        requireRole(agent, User.Role.DELIVERY_AGENT);
        if (!Boolean.TRUE.equals(agent.getDeliveryAgentActive())) {
            throw new AccessDeniedException("Your warehouse operator must activate your account before you can accept delivery tasks.");
        }
        if (!isWarehouseActive(agent.getDeliveryWarehouse())) {
            throw new AccessDeniedException("Your warehouse is not currently approved for delivery operations.");
        }
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        boolean finalLeg = fulfillment.getFinalDeliveryAgent() != null
                && fulfillment.getFinalDeliveryAgent().getId().equals(agent.getId())
                && fulfillment.getStatus() == FulfillmentStatus.AT_DESTINATION_STORE;
        boolean transferLeg = fulfillment.getTransferDeliveryAgent() != null
                && fulfillment.getTransferDeliveryAgent().getId().equals(agent.getId())
                && (fulfillment.getStatus() == FulfillmentStatus.PACKED_READY
                || fulfillment.getStatus() == FulfillmentStatus.IN_TRANSIT_TO_STORE);
        boolean pickupLeg = fulfillment.getDeliveryAgent() != null
                && fulfillment.getDeliveryAgent().getId().equals(agent.getId())
                && (fulfillment.getStatus() == FulfillmentStatus.CONFIRMED
                || fulfillment.getStatus() == FulfillmentStatus.PICKED_UP_FROM_OWNER);
        if (!finalLeg && !transferLeg && !pickupLeg) {
            throw new AccessDeniedException("This delivery offer is not assigned to your account.");
        }
        if (finalLeg && fulfillment.getFinalAgentAcceptedAt() != null
                || transferLeg && fulfillment.getTransferAgentAcceptedAt() != null
                || pickupLeg && fulfillment.getDeliveryAgentAcceptedAt() != null) {
            return toDto(fulfillment, true);
        }
        LocalDateTime offerExpiresAt = finalLeg ? fulfillment.getFinalAgentOfferExpiresAt()
                : transferLeg ? fulfillment.getTransferAgentOfferExpiresAt()
                : fulfillment.getDeliveryAgentOfferExpiresAt();
        if (offerExpiresAt == null || !offerExpiresAt.isAfter(now())) {
            if (finalLeg) {
                expireFinalAgentOffer(fulfillment, agent);
            } else if (transferLeg) {
                expireTransferAgentOffer(fulfillment, agent);
            } else {
                offerNextAgent(fulfillment, agent);
            }
            return toDto(fulfillment, true);
        }
        if (finalLeg) {
            fulfillment.setFinalAgentAcceptedAt(now());
            fulfillment.setFinalAgentRouteDeadline(now().plusHours(24));
        } else if (transferLeg) {
            fulfillment.setTransferAgentAcceptedAt(now());
            fulfillment.setTransferAgentRouteDeadline(now().plusHours(24));
        } else {
            fulfillment.setDeliveryAgentAcceptedAt(now());
            fulfillment.setDeliveryAgentRouteDeadline(now().plusHours(24));
        }
        record(fulfillment, agent, finalLeg ? "FINAL_DELIVERY_AGENT_ACCEPTED"
                        : transferLeg ? "TRANSFER_DELIVERY_AGENT_ACCEPTED" : "DELIVERY_AGENT_ACCEPTED",
                fulfillment.getStatus(),
                fulfillment.getStatus(), "SUCCESS", null, null);
        return toDto(fulfillment, true);
    }

    @Transactional
    public List<FulfillmentDto> refreshDeliveryOffers(String email) {
        User actor = getUser(email);
        requireRole(actor, User.Role.DELIVERY_AGENT, User.Role.ADMIN);
        if (actor.getRole() == User.Role.DELIVERY_AGENT
                && (!Boolean.TRUE.equals(actor.getDeliveryAgentActive())
                || !isWarehouseActive(actor.getDeliveryWarehouse()))) {
            throw new AccessDeniedException("Your warehouse operator must activate your account before you can receive delivery tasks.");
        }
        expireOffers();
        return getDeliveryTasks(email);
    }

    @Transactional
    public void expireOffers() {
        LocalDateTime at = now();
        fulfillmentRepository.findByDeliveryAgentAcceptedAtIsNullAndDeliveryAgentOfferExpiresAtBefore(at)
                .forEach(candidate -> {
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getDeliveryAgentAcceptedAt() == null
                            && fulfillment.getDeliveryAgentOfferExpiresAt() != null
                            && !fulfillment.getDeliveryAgentOfferExpiresAt().isAfter(at)) {
                        offerNextAgent(fulfillment, fulfillment.getDeliveryAgent());
                    }
                });
        fulfillmentRepository.findByFinalAgentAcceptedAtIsNullAndFinalAgentOfferExpiresAtBefore(at)
                .forEach(candidate -> {
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getFinalAgentAcceptedAt() == null
                            && fulfillment.getFinalAgentOfferExpiresAt() != null
                            && !fulfillment.getFinalAgentOfferExpiresAt().isAfter(at)) {
                        expireFinalAgentOffer(fulfillment, fulfillment.getFinalDeliveryAgent());
                    }
                });
        fulfillmentRepository.findByTransferAgentAcceptedAtIsNullAndTransferAgentOfferExpiresAtBefore(at)
                .forEach(candidate -> {
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getTransferAgentAcceptedAt() == null
                            && fulfillment.getTransferAgentOfferExpiresAt() != null
                            && !fulfillment.getTransferAgentOfferExpiresAt().isAfter(at)) {
                        expireTransferAgentOffer(fulfillment, fulfillment.getTransferDeliveryAgent());
                    }
                });
        fulfillmentRepository
                .findByDeliveryAgentAcceptedAtIsNotNullAndDeliveryAgentRouteDeadlineBefore(at)
                .forEach(candidate -> {
                    if (candidate.getStatus() != FulfillmentStatus.CONFIRMED
                            && candidate.getStatus() != FulfillmentStatus.PICKED_UP_FROM_OWNER) {
                        return;
                    }
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getDeliveryAgentRouteDeadline() != null
                            && !fulfillment.getDeliveryAgentRouteDeadline().isAfter(at)) {
                        offerNextAgent(fulfillment, fulfillment.getDeliveryAgent());
                    }
                });
        fulfillmentRepository
                .findByFinalAgentAcceptedAtIsNotNullAndFinalAgentRouteDeadlineBefore(at)
                .forEach(candidate -> {
                    if (candidate.getStatus() != FulfillmentStatus.AT_DESTINATION_STORE
                            && candidate.getStatus() != FulfillmentStatus.OUT_FOR_DELIVERY) {
                        return;
                    }
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getFinalAgentRouteDeadline() != null
                            && !fulfillment.getFinalAgentRouteDeadline().isAfter(at)) {
                        expireFinalAgentOffer(fulfillment, fulfillment.getFinalDeliveryAgent());
                    }
                });
        fulfillmentRepository
                .findByTransferAgentAcceptedAtIsNotNullAndTransferAgentRouteDeadlineBefore(at)
                .forEach(candidate -> {
                    if (candidate.getStatus() != FulfillmentStatus.PACKED_READY
                            && candidate.getStatus() != FulfillmentStatus.IN_TRANSIT_TO_STORE) {
                        return;
                    }
                    SellerFulfillment fulfillment = getLockedFulfillment(candidate.getId());
                    if (fulfillment.getTransferAgentRouteDeadline() != null
                            && !fulfillment.getTransferAgentRouteDeadline().isAfter(at)) {
                        expireTransferAgentOffer(fulfillment, fulfillment.getTransferDeliveryAgent());
                    }
                });
    }

    @Transactional(readOnly = true)
    public DeliveryScanResultDto validateQr(String email, String rawToken) {
        User actor = getUser(email);
        DeliveryStageToken token = getToken(rawToken);
        ensurePackageQrUsable(token);
        if (token.getStage() != Stage.PICKUP_FROM_OWNER) {
            throw new ConflictException("Scan the original package QR to continue delivery.");
        }
        SellerFulfillment fulfillment = token.getFulfillment();
        Stage activeStage = activeStage(fulfillment);
        if (activeStage == null) {
            throw new ConflictException("This order has no pending delivery stage.");
        }
        if (activeStage == Stage.WAREHOUSE_RECEIVED) {
            throw new ConflictException(
                    "Scan the origin warehouse arrival QR to confirm this package reached the hub.");
        }
        authorizeForStage(actor, fulfillment, activeStage);
        validateStageStatus(fulfillment, activeStage);
        return DeliveryScanResultDto.builder()
                .stage(activeStage.name())
                .fulfillment(toDto(fulfillment, canSeeCustomerAddress(actor)))
                .build();
    }

    @Transactional
    public OwnerPickupQrTokenDto getQrToken(String email, Long fulfillmentId, Stage stage) {
        User actor = getUser(email);
        if (stage != Stage.PICKUP_FROM_OWNER) {
            throw new ConflictException("This workflow uses one reusable package QR.");
        }
        SellerFulfillment fulfillment = fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller fulfillment", fulfillmentId));
        authorizeTokenView(actor, fulfillment, stage);
        DeliveryStageToken token = tokenRepository
                .findTopByFulfillmentIdAndStageOrderByIssuedAtDesc(fulfillmentId, stage)
                .orElseThrow(() -> new ResourceNotFoundException("QR token", fulfillmentId));
        ensureTokenUsable(token);
        try {
            return new OwnerPickupQrTokenDto(
                    secureTokenService.decrypt(token.getTokenCiphertext()), false);
        } catch (IllegalStateException exception) {
            if (!"Unable to decrypt delivery token".equals(exception.getMessage())) {
                throw exception;
            }
            token.setRevokedAt(now());
            DeliveryStageToken replacement = createToken(fulfillment, stage);
            record(fulfillment, actor, "QR_TOKEN_REGENERATED", fulfillment.getStatus(),
                    fulfillment.getStatus(), "SUCCESS", null,
                    "The saved pickup QR could not be recovered; a replacement was generated.");
            return new OwnerPickupQrTokenDto(
                    secureTokenService.decrypt(replacement.getTokenCiphertext()), true);
        }
    }

    @Transactional
    public String replaceQrToken(String email, Long fulfillmentId, Stage stage) {
        User administrator = getUser(email);
        requireRole(administrator, User.Role.ADMIN);
        if (stage != Stage.PICKUP_FROM_OWNER) {
            throw new ConflictException("Only the package QR can be replaced.");
        }
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        validateStageStatus(fulfillment, stage);
        DeliveryStageToken previous = tokenRepository
                .findTopByFulfillmentIdAndStageOrderByIssuedAtDesc(fulfillmentId, stage)
                .orElseThrow(() -> new ResourceNotFoundException("QR token", fulfillmentId));
        if (previous.getUsedAt() != null) {
            throw new ConflictException("A used QR token cannot be replaced.");
        }
        previous.setRevokedAt(now());
        DeliveryStageToken replacement = createToken(fulfillment, stage);
        record(fulfillment, administrator, "QR_TOKEN_REPLACED", fulfillment.getStatus(),
                fulfillment.getStatus(), "SUCCESS", null, stage.name());
        return secureTokenService.decrypt(replacement.getTokenCiphertext());
    }

    @Transactional
    public StageResultDto submitStage(String email, String rawToken, StageSubmissionRequest request,
                                      Stage requestedStage) {
        User actor = getUser(email);
        DeliveryStageToken token = getLockedToken(rawToken);
        ensurePackageQrUsable(token);
        if (token.getStage() != Stage.PICKUP_FROM_OWNER) {
            throw new ConflictException("Scan the original package QR to continue delivery.");
        }
        if (requestedStage == Stage.WAREHOUSE_RECEIVED) {
            throw new ConflictException(
                    "Scan the origin warehouse arrival QR to confirm this package reached the hub.");
        }
        SellerFulfillment fulfillment = getLockedFulfillment(token.getFulfillment().getId());
        if (activeStage(fulfillment) != requestedStage) {
            throw new ConflictException("The order has changed. Scan the package QR again for its current stage.");
        }
        authorizeForStage(actor, fulfillment, requestedStage);
        validateStageStatus(fulfillment, requestedStage);
        LocalDateTime now = now();
        FulfillmentStatus previous = fulfillment.getStatus();
        FulfillmentStatus next = switch (requestedStage) {
            case PICKUP_FROM_OWNER -> FulfillmentStatus.PICKED_UP_FROM_OWNER;
            case WAREHOUSE_RECEIVED -> FulfillmentStatus.PACKED_READY;
            case OUT_FOR_DELIVERY_TO_STORE -> FulfillmentStatus.IN_TRANSIT_TO_STORE;
            case STORE_RECEIVED -> FulfillmentStatus.AT_DESTINATION_STORE;
            case OUT_FOR_DELIVERY -> FulfillmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> throw new ConflictException(
                    "Final delivery requires customer confirmation.");
        };
        setTransitionTime(fulfillment, requestedStage, now);
        fulfillment.setStatus(next);
        record(fulfillment, actor, requestedStage.name(), previous, next, "SUCCESS",
                request.getPackageCondition(), request.getNotes());
        refreshOrderStatus(fulfillment.getOrder());
        notifyStage(fulfillment, requestedStage);
        if (requestedStage == Stage.STORE_RECEIVED) {
            prepareFinalDelivery(fulfillment);
        }

        return StageResultDto.builder()
                .fulfillment(toDto(fulfillment, canSeeCustomerAddress(actor)))
                .nextStage(activeStage(fulfillment) == null
                        ? null : activeStage(fulfillment).name())
                .build();
    }

    @Transactional
    public FulfillmentDto requestCustomerDeliveryConfirmation(
            String email, String rawToken, String notes) {
        User actor = getUser(email);
        DeliveryStageToken token = getLockedToken(rawToken);
        ensurePackageQrUsable(token);
        SellerFulfillment fulfillment = getLockedFulfillment(token.getFulfillment().getId());
        if (token.getStage() != Stage.PICKUP_FROM_OWNER
                || activeStage(fulfillment) != Stage.DELIVERED) {
            throw new ConflictException("This package is not ready for delivery confirmation.");
        }
        authorizeForStage(actor, fulfillment, Stage.DELIVERED);
        validateStageStatus(fulfillment, Stage.DELIVERED);
        FulfillmentStatus previous = fulfillment.getStatus();
        token.setUsedAt(now());
        fulfillment.setStatus(FulfillmentStatus.DELIVERED);
        fulfillment.setDeliveredAt(now());
        fulfillment.setCustomerDeliveryResponse("DELIVERED");
        fulfillment.setCustomerDeliveryResponseAt(now());
        revokeUnusedTokens(fulfillment);
        Order order = fulfillment.getOrder();
        record(fulfillment, actor, "DELIVERY_AGENT_CONFIRMED_DELIVERY", previous,
                fulfillment.getStatus(), "SUCCESS", null, notes);
        refreshOrderStatus(order);
        String congratulations = "Congratulations! Order " + order.getOrderNumber()
                + " was delivered successfully.";
        notificationService.notify(order.getUser(), order, fulfillment,
                "DELIVERY_CONFIRMED", "Congratulations! Your order was delivered", congratulations);
        notificationService.notify(fulfillment.getOwner(), order, fulfillment,
                "DELIVERY_COMPLETED", "Order delivered",
                "Order " + order.getOrderNumber() + " was delivered by the assigned agent.");
        notificationService.notify(fulfillment.getFinalDeliveryAgent(), order, fulfillment,
                "DELIVERY_COMPLETED", "Delivery completed",
                "Congratulations! You completed delivery for order " + order.getOrderNumber() + ".");
        WarehouseLocation destination = fulfillment.getDestinationWarehouseLocation();
        if (destination != null) {
            notificationService.notify(destination.getOperator(), order, fulfillment,
                    "DELIVERY_COMPLETED", "Great work! Delivery completed",
                    congratulations);
        }
        return toDto(fulfillment, true);
    }

    @Transactional
    public FulfillmentDto confirmCustomerDelivery(String email, Long fulfillmentId,
                                                   CustomerDeliveryConfirmationRequest request) {
        User customer = getUser(email);
        SellerFulfillment fulfillment = getLockedFulfillment(fulfillmentId);
        if (!fulfillment.getOrder().getUser().getId().equals(customer.getId())) {
            throw new AccessDeniedException("You cannot confirm another customer's delivery.");
        }
        boolean delivered = Boolean.TRUE.equals(request.getDelivered());
        if (fulfillment.getStatus() == FulfillmentStatus.DELIVERED && delivered) {
            return toDto(fulfillment, false);
        }
        if (fulfillment.getStatus() != FulfillmentStatus.DELIVERY_CONFIRMATION_PENDING) {
            throw new ConflictException("There is no pending delivery confirmation for this order.");
        }
        String response = delivered ? "DELIVERED" : "NOT_RECEIVED";
        if (response.equals(fulfillment.getCustomerDeliveryResponse())) {
            return toDto(fulfillment, false);
        }
        if (!delivered) {
            fulfillment.setCustomerDeliveryResponse(response);
            fulfillment.setCustomerDeliveryResponseAt(now());
            record(fulfillment, customer, "CUSTOMER_REPORTED_NOT_RECEIVED",
                    fulfillment.getStatus(), fulfillment.getStatus(), "REPORTED",
                    null, request.getNotes());
            notificationService.notify(fulfillment.getDeliveryAgent(), fulfillment.getOrder(),
                    fulfillment, "DELIVERY_NOT_RECEIVED", "Customer reports package not received",
                    "The customer has not confirmed delivery for order "
                            + fulfillment.getOrder().getOrderNumber() + ". Please follow up.");
            notificationService.notify(fulfillment.getOwner(), fulfillment.getOrder(),
                    fulfillment, "DELIVERY_NOT_RECEIVED", "Delivery needs follow-up",
                    "The customer has not confirmed delivery for order "
                            + fulfillment.getOrder().getOrderNumber() + ".");
            return toDto(fulfillment, false);
        }

        FulfillmentStatus previous = fulfillment.getStatus();
        fulfillment.setCustomerDeliveryResponse(response);
        fulfillment.setCustomerDeliveryResponseAt(now());
        fulfillment.setStatus(FulfillmentStatus.DELIVERED);
        fulfillment.setDeliveredAt(now());
        revokeUnusedTokens(fulfillment);
        record(fulfillment, customer, "CUSTOMER_CONFIRMED_DELIVERY", previous,
                fulfillment.getStatus(), "SUCCESS", null, request.getNotes());
        refreshOrderStatus(fulfillment.getOrder());
        String message = "Congratulations! Your order has been delivered successfully.";
        notificationService.notify(customer, fulfillment.getOrder(), fulfillment,
                "DELIVERY_CONFIRMED", "Delivery confirmed", message);
        notificationService.notify(fulfillment.getOwner(), fulfillment.getOrder(), fulfillment,
                "DELIVERY_COMPLETED", "Order delivered",
                "Order " + fulfillment.getOrder().getOrderNumber() + " was confirmed delivered.");
        notificationService.notify(fulfillment.getDeliveryAgent(), fulfillment.getOrder(), fulfillment,
                "DELIVERY_COMPLETED", "Delivery completed",
                "Order " + fulfillment.getOrder().getOrderNumber() + " was confirmed delivered.");
        return toDto(fulfillment, false);
    }

    @Transactional(readOnly = true)
    public List<DeliveryEventDto> getHistory(String email, Long fulfillmentId) {
        User actor = getUser(email);
        SellerFulfillment fulfillment = fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller fulfillment", fulfillmentId));
        authorizeRead(actor, fulfillment);
        return eventRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillmentId).stream()
                .map(this::toDeliveryEventDto)
                .toList();
    }

    @Transactional
    public void revokeForOrder(Order order, User actor) {
        for (SellerFulfillment fulfillment : order.getFulfillments()) {
            FulfillmentStatus previous = fulfillment.getStatus();
            if (previous != FulfillmentStatus.DELIVERED
                    && previous != FulfillmentStatus.REJECTED
                    && previous != FulfillmentStatus.CANCELLED) {
                fulfillment.setStatus(FulfillmentStatus.CANCELLED);
                revokeUnusedTokens(fulfillment);
                record(fulfillment, actor, "ORDER_CANCELLED", previous,
                        fulfillment.getStatus(), "SUCCESS", null, null);
            }
        }
    }

    @Transactional
    public void cancelFulfillmentIfEmpty(Order order, OrderItem cancelledItem, User actor) {
        SellerFulfillment fulfillment = cancelledItem.getFulfillment();
        if (fulfillment == null || fulfillment.getItems().stream().anyMatch(item -> !item.isCancelled())) {
            return;
        }
        SellerFulfillment locked = getLockedFulfillment(fulfillment.getId());
        FulfillmentStatus previous = locked.getStatus();
        if (previous == FulfillmentStatus.DELIVERED) {
            throw new ConflictException("A delivered fulfillment cannot be cancelled.");
        }
        locked.setStatus(FulfillmentStatus.CANCELLED);
        revokeUnusedTokens(locked);
        record(locked, actor, "SELLER_ITEMS_CANCELLED", previous, locked.getStatus(),
                "SUCCESS", null, null);
        refreshOrderStatus(order);
    }

    private void notifyStage(SellerFulfillment fulfillment, Stage stage) {
        String orderNumber = fulfillment.getOrder().getOrderNumber();
        switch (stage) {
            case PICKUP_FROM_OWNER -> notificationService.notify(
                    fulfillment.getOrder().getUser(), fulfillment.getOrder(), fulfillment,
                    "PICKED_UP_FROM_OWNER", "Package collected from seller",
                    "The package for order " + orderNumber + " has been collected from the seller.");
            case WAREHOUSE_RECEIVED -> notificationService.notify(
                    fulfillment.getOrder().getUser(), fulfillment.getOrder(), fulfillment,
                    "WAREHOUSE_RECEIVED", "Package received at warehouse",
                    "The package for order " + orderNumber + " has been received at our warehouse.");
            case OUT_FOR_DELIVERY_TO_STORE -> notificationService.notify(
                    fulfillment.getOrder().getUser(), fulfillment.getOrder(), fulfillment,
                    "IN_TRANSIT_TO_STORE", "Package moving between warehouses",
                    "The package for order " + orderNumber
                            + " is on its way from the origin warehouse to the warehouse nearest to you.");
            case STORE_RECEIVED -> notificationService.notify(
                    fulfillment.getOrder().getUser(), fulfillment.getOrder(), fulfillment,
                    "AT_DESTINATION_STORE", "Package reached your local warehouse",
                    "The package for order " + orderNumber
                            + " has reached the warehouse nearest to you and is being prepared for final delivery.");
            case OUT_FOR_DELIVERY -> notificationService.notify(
                    fulfillment.getOrder().getUser(), fulfillment.getOrder(), fulfillment,
                    "OUT_FOR_DELIVERY", "Your package is on its way",
                    "Order " + orderNumber + " is out for delivery.");
            case DELIVERED -> throw new ConflictException("Delivery is recorded by the final delivery agent.");
        }
    }

    private DeliveryStageToken issueToken(SellerFulfillment fulfillment, Stage stage) {
        return tokenRepository.findTopByFulfillmentIdAndStageOrderByIssuedAtDesc(
                        fulfillment.getId(), stage)
                .map(existing -> {
                    if (existing.getUsedAt() != null || existing.getRevokedAt() != null
                            || existing.getExpiresAt().isBefore(now())) {
                        throw new ConflictException("The QR token for this stage is no longer usable.");
                    }
                    return existing;
                })
                .orElseGet(() -> createToken(fulfillment, stage));
    }

    private DeliveryStageToken createToken(SellerFulfillment fulfillment, Stage stage) {
        String rawToken = secureTokenService.newQrToken();
        DeliveryStageToken token = DeliveryStageToken.builder()
                .fulfillment(fulfillment)
                .stage(stage)
                .tokenHash(secureTokenService.hashToken(rawToken))
                .tokenCiphertext(secureTokenService.encrypt(rawToken))
                .expiresAt(now().plusHours(qrExpiryHours))
                .build();
        fulfillment.getStageTokens().add(token);
        return tokenRepository.save(token);
    }

    private void validateStageStatus(SellerFulfillment fulfillment, Stage stage) {
        FulfillmentStatus expected = switch (stage) {
            case PICKUP_FROM_OWNER -> FulfillmentStatus.CONFIRMED;
            case WAREHOUSE_RECEIVED -> FulfillmentStatus.PICKED_UP_FROM_OWNER;
            case OUT_FOR_DELIVERY_TO_STORE -> FulfillmentStatus.PACKED_READY;
            case STORE_RECEIVED -> isSingleWarehouseFulfillment(fulfillment)
                    ? FulfillmentStatus.PACKED_READY : FulfillmentStatus.IN_TRANSIT_TO_STORE;
            case OUT_FOR_DELIVERY -> FulfillmentStatus.AT_DESTINATION_STORE;
            case DELIVERED -> FulfillmentStatus.OUT_FOR_DELIVERY;
        };
        if (fulfillment.getStatus() != expected) {
            throw new ConflictException("This delivery stage is not available for the current order status.");
        }
        if (fulfillment.getOrder().getStatus() == OrderStatus.CANCELLED
                || fulfillment.getStatus() == FulfillmentStatus.REJECTED
                || fulfillment.getStatus() == FulfillmentStatus.CANCELLED) {
            throw new ConflictException("A rejected or cancelled order cannot continue delivery.");
        }
    }

    private void authorizeForStage(User actor, SellerFulfillment fulfillment, Stage stage) {
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }
        if (actor.getRole() == User.Role.WAREHOUSE_OPERATOR
                && (stage == Stage.WAREHOUSE_RECEIVED || stage == Stage.STORE_RECEIVED)) {
            authorizeWarehouseOperatorOrAdmin(actor, fulfillment,
                    stage == Stage.STORE_RECEIVED);
            return;
        }
        boolean finalLeg = stage == Stage.OUT_FOR_DELIVERY || stage == Stage.DELIVERED;
        if (stage == Stage.OUT_FOR_DELIVERY_TO_STORE) {
            authorizeTransferAgent(actor, fulfillment);
            return;
        }
        User assignedAgent = finalLeg
                ? fulfillment.getFinalDeliveryAgent() : fulfillment.getDeliveryAgent();
        LocalDateTime acceptedAt = finalLeg
                ? fulfillment.getFinalAgentAcceptedAt() : fulfillment.getDeliveryAgentAcceptedAt();
        if (actor.getRole() != User.Role.DELIVERY_AGENT
                || !Boolean.TRUE.equals(actor.getDeliveryAgentActive())
                || !isWarehouseActive(actor.getDeliveryWarehouse())
                || assignedAgent == null
                || !assignedAgent.getId().equals(actor.getId())) {
            throw new AccessDeniedException("This delivery task is not assigned to your account.");
        }
        if (acceptedAt == null) {
            throw new ConflictException("Accept the one-minute delivery offer before scanning this package.");
        }
        LocalDateTime routeDeadline = finalLeg
                ? fulfillment.getFinalAgentRouteDeadline()
                : fulfillment.getDeliveryAgentRouteDeadline();
        if (routeDeadline == null || !routeDeadline.isAfter(now())) {
            throw new ExpiredResourceException(
                    "The 24-hour delivery assignment expired. The task is being offered to another agent.");
        }
    }

    private void authorizeTokenView(User actor, SellerFulfillment fulfillment, Stage stage) {
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }
        if (stage == Stage.PICKUP_FROM_OWNER
                && actor.getRole() == User.Role.OWNER
                && fulfillment.getOwner() != null
                && fulfillment.getOwner().getId().equals(actor.getId())) {
            return;
        }
        authorizeForStage(actor, fulfillment, stage);
    }

    private void authorizeOwnerOrAdmin(User actor, SellerFulfillment fulfillment) {
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }
        if (actor.getRole() != User.Role.OWNER || fulfillment.getOwner() == null
                || !fulfillment.getOwner().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You cannot manage this seller fulfillment.");
        }
    }

    private void authorizeWarehouseOperatorOrAdmin(
            User actor, SellerFulfillment fulfillment, boolean destination) {
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }

        if (actor.getRole() != User.Role.WAREHOUSE_OPERATOR) {
            throw new AccessDeniedException("A warehouse operator account is required.");
        }
        WarehouseLocation expected = destination
                ? fulfillment.getDestinationWarehouseLocation()
                : fulfillment.getWarehouseLocation();
        WarehouseLocation actual = warehouseLocationRepository.findByOperatorId(actor.getId())
                .filter(location -> Boolean.TRUE.equals(location.getActive()))
                .orElseThrow(() -> new AccessDeniedException(
                        "Your warehouse must be approved before you can process packages."));
        if (expected == null || !actual.getId().equals(expected.getId())) {
            throw new AccessDeniedException(
                    "This package is assigned to a different warehouse.");
        }
    }

    private void authorizeTransferAgent(User actor, SellerFulfillment fulfillment) {
        if (actor.getRole() != User.Role.DELIVERY_AGENT
                || !Boolean.TRUE.equals(actor.getDeliveryAgentActive())
                || !isWarehouseActive(actor.getDeliveryWarehouse())
                || fulfillment.getTransferDeliveryAgent() == null
                || !fulfillment.getTransferDeliveryAgent().getId().equals(actor.getId())) {
            throw new AccessDeniedException("This warehouse transfer is not assigned to your account.");
        }
        if (fulfillment.getTransferAgentAcceptedAt() == null) {
            throw new ConflictException("Accept the one-minute transfer offer before scanning the package.");
        }
        if (fulfillment.getTransferAgentRouteDeadline() == null
                || !fulfillment.getTransferAgentRouteDeadline().isAfter(now())) {
            throw new ExpiredResourceException(
                    "The 24-hour transfer assignment expired. Ask the origin warehouse to assign another agent.");
        }
    }

    private void authorizeRead(User actor, SellerFulfillment fulfillment) {
        boolean customer = fulfillment.getOrder().getUser().getId().equals(actor.getId());
        boolean owner = fulfillment.getOwner() != null
                && fulfillment.getOwner().getId().equals(actor.getId());
        boolean agent = fulfillment.getDeliveryAgent() != null
                && fulfillment.getDeliveryAgent().getId().equals(actor.getId());
        boolean transferAgent = fulfillment.getTransferDeliveryAgent() != null
                && fulfillment.getTransferDeliveryAgent().getId().equals(actor.getId());
        boolean finalAgent = fulfillment.getFinalDeliveryAgent() != null
                && fulfillment.getFinalDeliveryAgent().getId().equals(actor.getId());
        boolean warehouse = actor.getRole() == User.Role.WAREHOUSE_OPERATOR
                && warehouseLocationRepository.findByOperatorId(actor.getId())
                .map(location -> fulfillment.getWarehouseLocation() != null
                        && location.getId().equals(fulfillment.getWarehouseLocation().getId())
                        || fulfillment.getDestinationWarehouseLocation() != null
                        && location.getId().equals(fulfillment.getDestinationWarehouseLocation().getId()))
                .orElse(false);
        if (!customer && !owner && !agent && !transferAgent && !finalAgent && !warehouse
                && actor.getRole() != User.Role.ADMIN) {
            throw new AccessDeniedException("You cannot view this delivery history.");
        }
    }

    private void ensureTokenUsable(DeliveryStageToken token) {
        if (token.getRevokedAt() != null || token.getUsedAt() != null) {
            throw new ConflictException("This QR token has already been used or revoked.");
        }
        if (token.getExpiresAt().isBefore(now())) {
            throw new ExpiredResourceException("This QR token has expired.");
        }
    }

    private void ensurePackageQrUsable(DeliveryStageToken token) {
        if (token.getRevokedAt() != null) {
            throw new ConflictException("This package QR has been revoked.");
        }
        if (token.getExpiresAt().isBefore(now())) {
            throw new ExpiredResourceException("This package QR has expired.");
        }
    }

    private Stage activeStage(SellerFulfillment fulfillment) {
        return switch (fulfillment.getStatus()) {
            case CONFIRMED -> Stage.PICKUP_FROM_OWNER;
            case PICKED_UP_FROM_OWNER -> Stage.WAREHOUSE_RECEIVED;
            case PACKED_READY -> isSingleWarehouseFulfillment(fulfillment)
                    ? Stage.STORE_RECEIVED : Stage.OUT_FOR_DELIVERY_TO_STORE;
            case IN_TRANSIT_TO_STORE -> Stage.STORE_RECEIVED;
            case AT_DESTINATION_STORE -> Stage.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> Stage.DELIVERED;
            case AWAITING_OWNER_CONFIRMATION, DELIVERY_CONFIRMATION_PENDING,
                    DELIVERED, REJECTED, CANCELLED -> null;
        };
    }

    private boolean isSingleWarehouseFulfillment(SellerFulfillment fulfillment) {
        return fulfillment.getWarehouseLocation() != null
                && fulfillment.getDestinationWarehouseLocation() != null
                && fulfillment.getWarehouseLocation().getId()
                .equals(fulfillment.getDestinationWarehouseLocation().getId());
    }

    private DeliveryStageToken getToken(String rawToken) {
        return tokenRepository.findByTokenHash(secureTokenService.hashToken(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("QR token"));
    }

    private DeliveryStageToken getLockedToken(String rawToken) {
        return tokenRepository.findByTokenHashForUpdate(secureTokenService.hashToken(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("QR token"));
    }

    private SellerFulfillment getLockedFulfillment(Long id) {
        return fulfillmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller fulfillment", id));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void requireRole(User user, User.Role... roles) {
        if (!Set.of(roles).contains(user.getRole())) {
            throw new AccessDeniedException("Your account is not permitted to perform this operation.");
        }
    }

    private void setTransitionTime(SellerFulfillment fulfillment, Stage stage, LocalDateTime at) {
        switch (stage) {
            case PICKUP_FROM_OWNER -> fulfillment.setPickupAt(at);
            case WAREHOUSE_RECEIVED -> fulfillment.setWarehouseReceivedAt(at);
            case OUT_FOR_DELIVERY_TO_STORE -> fulfillment.setDispatchedAt(at);
            case STORE_RECEIVED -> { }
            case OUT_FOR_DELIVERY -> fulfillment.setDispatchedAt(at);
            case DELIVERED -> throw new ConflictException("Final delivery is customer-confirmed.");
        }
    }

    private Stage nextStage(Stage current) {
        return switch (current) {
            case PICKUP_FROM_OWNER -> Stage.WAREHOUSE_RECEIVED;
            case WAREHOUSE_RECEIVED -> Stage.OUT_FOR_DELIVERY_TO_STORE;
            case OUT_FOR_DELIVERY_TO_STORE -> Stage.STORE_RECEIVED;
            case STORE_RECEIVED -> Stage.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> Stage.DELIVERED;
            case DELIVERED -> throw new ConflictException("No QR stage follows delivery.");
        };
    }

    private void refreshOrderStatus(Order order) {
        List<SellerFulfillment> active = fulfillmentRepository.findAllByOrderId(order.getId())
                .stream()
                .filter(item -> item.getStatus() != FulfillmentStatus.REJECTED
                        && item.getStatus() != FulfillmentStatus.CANCELLED)
                .toList();
        if (active.isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setExpectedDeliveryDate(null);
            return;
        }
        if (active.stream().anyMatch(item ->
                item.getStatus() == FulfillmentStatus.AWAITING_OWNER_CONFIRMATION)) {
            order.setStatus(OrderStatus.PENDING);
        } else {
            FulfillmentStatus earliest = active.stream()
                    .map(SellerFulfillment::getStatus)
                    .min(Comparator.comparingInt(this::progressRank))
                    .orElseThrow();
            order.setStatus(switch (earliest) {
                case CONFIRMED -> OrderStatus.CONFIRMED;
                case PICKED_UP_FROM_OWNER -> OrderStatus.PICKED_UP_FROM_OWNER;
                case PACKED_READY -> OrderStatus.PACKED_READY;
                case IN_TRANSIT_TO_STORE -> OrderStatus.IN_TRANSIT_TO_STORE;
                case AT_DESTINATION_STORE -> OrderStatus.AT_DESTINATION_STORE;
                case OUT_FOR_DELIVERY -> OrderStatus.OUT_FOR_DELIVERY;
                case DELIVERY_CONFIRMATION_PENDING -> OrderStatus.DELIVERY_CONFIRMATION_PENDING;
                case DELIVERED -> OrderStatus.DELIVERED;
                case AWAITING_OWNER_CONFIRMATION -> OrderStatus.PENDING;
                case REJECTED, CANCELLED -> OrderStatus.CANCELLED;
            });
        }
        order.setExpectedDeliveryDate(active.stream()
                .map(SellerFulfillment::getExpectedDeliveryDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null));
        orderRepository.save(order);
    }

    private int progressRank(FulfillmentStatus status) {
        return switch (status) {
            case CONFIRMED -> 0;
            case PICKED_UP_FROM_OWNER -> 1;
            case PACKED_READY -> 2;
            case IN_TRANSIT_TO_STORE -> 3;
            case AT_DESTINATION_STORE -> 4;
            case OUT_FOR_DELIVERY -> 5;
            case DELIVERY_CONFIRMATION_PENDING -> 6;
            case DELIVERED -> 7;
            case AWAITING_OWNER_CONFIRMATION, REJECTED, CANCELLED -> Integer.MAX_VALUE;
        };
    }

    private void recalculateUnpaidOrder(Order order) {
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID
                || order.getPaymentStatus() == Order.PaymentStatus.REFUND_PENDING) {
            return;
        }
        BigDecimal subtotal = order.getItems().stream()
                .filter(item -> !item.isCancelled())
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivery = subtotal.signum() == 0 ? BigDecimal.ZERO
                : subtotal.compareTo(new BigDecimal("500")) > 0
                ? BigDecimal.ZERO : new BigDecimal("10");
        order.setSubtotal(subtotal);
        order.setDeliveryCharge(delivery);
        order.setTotal(subtotal.add(delivery));
    }

    private void revokeUnusedTokens(SellerFulfillment fulfillment) {
        for (DeliveryStageToken token : tokenRepository.findByFulfillmentId(fulfillment.getId())) {
            if (token.getUsedAt() == null && token.getRevokedAt() == null) {
                token.setRevokedAt(now());
            }
        }
    }

    private void record(SellerFulfillment fulfillment, User actor, String type,
                       FulfillmentStatus previous, FulfillmentStatus next, String outcome,
                       String packageCondition, String notes) {
        eventRepository.save(DeliveryEvent.builder()
                .fulfillment(fulfillment)
                .actor(actor)
                .eventType(type)
                .previousStatus(previous == null ? null : previous.name())
                .newStatus(next == null ? null : next.name())
                .outcome(outcome)
                .packageCondition(packageCondition)
                .notes(notes)
                .build());
    }

    private void offerNextAgent(SellerFulfillment fulfillment, User previousAgent) {
        WarehouseLocation warehouse = fulfillment.getWarehouseLocation();
        if (warehouse == null) {
            throw new ConflictException("The origin warehouse is not set for this delivery.");
        }
        Set<Long> alreadyOffered = eventRepository
                .findByFulfillmentIdAndEventType(fulfillment.getId(), "DELIVERY_AGENT_OFFERED")
                .stream()
                .map(DeliveryEvent::getActor)
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (previousAgent != null) {
            alreadyOffered.add(previousAgent.getId());
        }
        userRepository.findByDeliveryWarehouse_IdAndRoleAndDeliveryAgentActiveTrueOrderByCreatedAtAsc(
                        warehouse.getId(), User.Role.DELIVERY_AGENT)
                .stream()
                .filter(agent -> isWarehouseActive(agent.getDeliveryWarehouse()))
                .filter(agent -> !alreadyOffered.contains(agent.getId()))
                .findFirst()
                .ifPresentOrElse(agent -> offerAgent(fulfillment, agent, false), () -> {
                    fulfillment.setDeliveryAgent(null);
                    fulfillment.setDeliveryAgentAcceptedAt(null);
                    fulfillment.setDeliveryAgentOfferExpiresAt(null);
                    notificationService.notifyAdmins(
                            userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN),
                            fulfillment.getOrder(), fulfillment, "DELIVERY_AGENT_UNAVAILABLE",
                            "No delivery agent available",
                            "No active delivery agent is available at "
                                    + warehouse.getName() + " for order "
                                    + fulfillment.getOrder().getOrderNumber() + ".");
                });
    }

    private void prepareFinalDelivery(SellerFulfillment fulfillment) {
        User previousAgent = fulfillment.getFinalDeliveryAgent();
        fulfillment.setFinalDeliveryAgent(null);
        fulfillment.setFinalAgentOfferExpiresAt(null);
        fulfillment.setFinalAgentAcceptedAt(null);
        fulfillment.setFinalAgentRouteDeadline(null);
        if (previousAgent != null) {
            record(fulfillment, previousAgent, "FINAL_DELIVERY_ASSIGNMENT_CLEARED",
                    fulfillment.getStatus(), fulfillment.getStatus(), "SUCCESS", null,
                    "Destination warehouse must choose the final-delivery agent.");
            notificationService.notify(previousAgent, fulfillment.getOrder(), fulfillment,
                    "FINAL_DELIVERY_ASSIGNMENT_CLEARED", "Final delivery assignment changed",
                    "Order " + fulfillment.getOrder().getOrderNumber()
                            + " has reached the destination warehouse. Its operator will select the final-delivery agent.");
        }
        notifyDestinationWarehouse(fulfillment);
    }

    private void notifyDestinationWarehouse(SellerFulfillment fulfillment) {
        User operator = fulfillment.getDestinationWarehouseLocation().getOperator();
        if (operator != null) {
            notificationService.notify(operator, fulfillment.getOrder(), fulfillment,
                    "WAREHOUSE_HANDOFF_READY", "Package arrived at your warehouse",
                    "Order " + fulfillment.getOrder().getOrderNumber()
                            + " has arrived. Choose an active delivery agent for the final customer delivery.");
        }
    }

    private void offerAgent(SellerFulfillment fulfillment, User agent, boolean finalLeg) {
        LocalDateTime expiresAt = now().plusMinutes(1);
        if (finalLeg) {
            fulfillment.setFinalDeliveryAgent(agent);
            fulfillment.setFinalAgentAcceptedAt(null);
            fulfillment.setFinalAgentRouteDeadline(null);
            fulfillment.setFinalAgentOfferExpiresAt(expiresAt);
        } else {
            fulfillment.setDeliveryAgent(agent);
            fulfillment.setDeliveryAgentAcceptedAt(null);
            fulfillment.setDeliveryAgentRouteDeadline(null);
            fulfillment.setDeliveryAgentOfferExpiresAt(expiresAt);
        }
        record(fulfillment, agent,
                finalLeg ? "FINAL_DELIVERY_AGENT_OFFERED" : "DELIVERY_AGENT_OFFERED",
                null, fulfillment.getStatus(), "SUCCESS", null, null);
        notificationService.notify(agent, fulfillment.getOrder(), fulfillment,
                finalLeg ? "FINAL_DELIVERY_ASSIGNMENT" : "DELIVERY_ASSIGNMENT",
                finalLeg ? "Final delivery offer · 1 minute to accept"
                        : "Delivery task offer · 1 minute to accept",
                (finalLeg ? "Accept the final delivery route" : "Accept the delivery route")
                        + " for order " + fulfillment.getOrder().getOrderNumber()
                        + " within one minute.");
    }

    private void offerTransferAgent(SellerFulfillment fulfillment, User agent) {
        LocalDateTime expiresAt = now().plusMinutes(1);
        fulfillment.setTransferDeliveryAgent(agent);
        fulfillment.setTransferAgentAcceptedAt(null);
        fulfillment.setTransferAgentRouteDeadline(null);
        fulfillment.setTransferAgentOfferExpiresAt(expiresAt);
        record(fulfillment, agent, "TRANSFER_DELIVERY_AGENT_OFFERED", null,
                fulfillment.getStatus(), "SUCCESS", null, null);
        notificationService.notify(agent, fulfillment.getOrder(), fulfillment,
                "TRANSFER_DELIVERY_ASSIGNMENT", "Warehouse transfer offer · 1 minute to accept",
                "Accept the warehouse transfer for order " + fulfillment.getOrder().getOrderNumber()
                        + ". Collect it at " + fulfillment.getWarehouseLocation().getName()
                        + " and deliver it to " + fulfillment.getDestinationWarehouseLocation().getName()
                        + " within 24 hours.");
    }

    private void expireTransferAgentOffer(SellerFulfillment fulfillment, User previousAgent) {
        fulfillment.setTransferDeliveryAgent(null);
        fulfillment.setTransferAgentAcceptedAt(null);
        fulfillment.setTransferAgentOfferExpiresAt(null);
        fulfillment.setTransferAgentRouteDeadline(null);
        record(fulfillment, previousAgent, "TRANSFER_DELIVERY_AGENT_OFFER_EXPIRED",
                fulfillment.getStatus(), fulfillment.getStatus(), "EXPIRED", null, null);
        WarehouseLocation warehouse = fulfillment.getWarehouseLocation();
        if (warehouse != null && warehouse.getOperator() != null) {
            notificationService.notify(warehouse.getOperator(), fulfillment.getOrder(), fulfillment,
                    "TRANSFER_AGENT_REQUIRED", "Choose another transfer agent",
                    "The transfer offer for order " + fulfillment.getOrder().getOrderNumber()
                            + " expired or its 24-hour assignment ended. Assign another active agent.");
        }
    }

    private void expireFinalAgentOffer(SellerFulfillment fulfillment, User previousAgent) {
        fulfillment.setFinalDeliveryAgent(null);
        fulfillment.setFinalAgentAcceptedAt(null);
        fulfillment.setFinalAgentOfferExpiresAt(null);
        fulfillment.setFinalAgentRouteDeadline(null);
        record(fulfillment, previousAgent, "FINAL_DELIVERY_AGENT_OFFER_EXPIRED",
                fulfillment.getStatus(), fulfillment.getStatus(), "EXPIRED", null, null);
        notifyDestinationWarehouse(fulfillment);
    }

    private FulfillmentDto toDto(SellerFulfillment fulfillment, boolean includeCustomerAddress) {
        Order order = fulfillment.getOrder();
        return FulfillmentDto.builder()
                .id(fulfillment.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
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
                .destinationWarehouseLocationId(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getId())
                .destinationWarehouseName(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getName())
                .destinationWarehouseStreet(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getStreet())
                .destinationWarehouseCity(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getCity())
                .destinationWarehousePostalCode(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getPostalCode())
                .destinationWarehouseLatitude(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getLatitude())
                .destinationWarehouseLongitude(fulfillment.getDestinationWarehouseLocation() == null
                        ? null : fulfillment.getDestinationWarehouseLocation().getLongitude())
                .customerName(includeCustomerAddress ? order.getAddressFullName() : null)
                .customerPhone(includeCustomerAddress ? order.getUser().getPhoneNumber() : null)
                .customerStreet(includeCustomerAddress ? order.getAddressStreet() : null)
                .customerCity(includeCustomerAddress ? order.getAddressCity() : null)
                .customerPostalCode(includeCustomerAddress ? order.getAddressPostalCode() : null)
                .customerLatitude(includeCustomerAddress ? order.getAddressLatitude() : null)
                .customerLongitude(includeCustomerAddress ? order.getAddressLongitude() : null)
                .pickupName(fulfillment.getPickupName())
                .pickupStreet(fulfillment.getPickupStreet())
                .pickupCity(fulfillment.getPickupCity())
                .pickupState(fulfillment.getPickupState())
                .pickupPostalCode(fulfillment.getPickupPostalCode())
                .pickupLatitude(fulfillment.getPickupLocation() == null
                        ? null : fulfillment.getPickupLocation().getLatitude())
                .pickupLongitude(fulfillment.getPickupLocation() == null
                        ? null : fulfillment.getPickupLocation().getLongitude())
                .placedAt(order.getPlacedAt())
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
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isWarehouseActive(WarehouseLocation warehouse) {
        return warehouse != null && Boolean.TRUE.equals(warehouse.getActive());
    }

    private double distanceKm(double latitude1, double longitude1,
                              double latitude2, double longitude2) {
        double earthRadiusKm = 6371.0;
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private boolean canSeeCustomerAddress(User actor) {
        return actor.getRole() == User.Role.DELIVERY_AGENT || actor.getRole() == User.Role.ADMIN;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
