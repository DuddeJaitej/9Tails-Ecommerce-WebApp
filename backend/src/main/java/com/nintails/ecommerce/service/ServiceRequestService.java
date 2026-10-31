package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ServiceRequestCreateRequest;
import com.nintails.ecommerce.dto.ServiceRequestDto;
import com.nintails.ecommerce.entity.*;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final ServiceRequestRepository requestRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final WarehouseLocationRepository warehouseRepository;
    private final NotificationService notificationService;

    @Transactional
    public ServiceRequestDto create(String email, Long orderId, Long itemId,
                                    ServiceRequestCreateRequest request) {
        User customer = getUser(email);
        Order order = orderRepository.findByIdAndUserId(orderId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        OrderItem item = orderItemRepository.findByIdAndOrderAndUserForUpdate(
                        itemId, orderId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order item", itemId));
        SellerFulfillment fulfillment = item.getFulfillment();
        if (item.isCancelled() || fulfillment == null
                || fulfillment.getStatus() != SellerFulfillment.FulfillmentStatus.DELIVERED
                || fulfillment.getDeliveredAt() == null) {
            throw new BadRequestException("A return or exchange is available only for a delivered item.");
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (now.isAfter(fulfillment.getDeliveredAt().plusDays(7))) {
            throw new BadRequestException("Return and exchange requests must be submitted within 7 days of delivery.");
        }
        requestRepository.findByOrderItem_Order_IdOrderByRequestedAtDesc(orderId).stream()
                .filter(existing -> existing.getOrderItem().getId().equals(itemId))
                .findFirst()
                .filter(existing -> existing.getStatus() != ServiceRequest.Status.REJECTED)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "A return or exchange request already exists for this product.");
                });
        validateEvidenceUrl(request.getPhotoUrl(), false);
        validateEvidenceUrl(request.getVideoUrl(), true);

        WarehouseLocation warehouse = fulfillment.getDestinationWarehouseLocation() != null
                ? fulfillment.getDestinationWarehouseLocation() : fulfillment.getWarehouseLocation();
        if (warehouse == null || !Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ConflictException("The delivery warehouse is unavailable for return processing.");
        }
        ServiceRequest saved = requestRepository.save(ServiceRequest.builder()
                .orderItem(item)
                .type(request.getType())
                .reason(request.getReason())
                .details(request.getDetails().trim())
                .photoUrl(request.getPhotoUrl())
                .videoUrl(request.getVideoUrl())
                .warehouse(warehouse)
                .status(ServiceRequest.Status.REQUESTED)
                .build());
        if (request.getType() == ServiceRequest.Type.RETURN) {
            order.setReturnRequested(true);
        } else {
            order.setExchangeRequested(true);
        }
        String message = request.getType().name() + " requested for "
                + item.getProductName() + " in order " + order.getOrderNumber()
                + ". Reason: " + request.getReason().name().replace('_', ' ')
                + ". Details: " + request.getDetails().trim();
        notificationService.notify(fulfillment.getOwner(), order, fulfillment,
                "SERVICE_REQUESTED", "Customer requested a " + request.getType().name().toLowerCase(Locale.ROOT),
                message);
        notificationService.notify(warehouse.getOperator(), order, fulfillment,
                "SERVICE_REQUESTED", "Customer return/exchange needs review",
                message + " The seller must review this request before pickup is assigned.");
        return toDto(saved);
    }

    @Transactional
    public ServiceRequestDto review(String email, Long requestId, String decision, String notes) {
        User reviewer = getUser(email);
        ServiceRequest request = getLockedRequest(requestId);
        SellerFulfillment fulfillment = getFulfillment(request);
        if (reviewer.getRole() != User.Role.ADMIN
                && (reviewer.getRole() != User.Role.OWNER || fulfillment.getOwner() == null
                || !fulfillment.getOwner().getId().equals(reviewer.getId()))) {
            throw new AccessDeniedException("Only this product's owner can review the request.");
        }
        if (request.getStatus() != ServiceRequest.Status.REQUESTED) {
            throw new ConflictException("This service request has already been reviewed.");
        }
        if (decision == null || (!decision.equalsIgnoreCase("APPROVE")
                && !decision.equalsIgnoreCase("REJECT"))) {
            throw new BadRequestException("Choose approve or reject for this request.");
        }
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now(BUSINESS_ZONE));
        request.setReviewNotes(notes == null || notes.isBlank() ? null : notes.trim());
        request.setStatus(decision.equalsIgnoreCase("APPROVE")
                ? ServiceRequest.Status.APPROVED : ServiceRequest.Status.REJECTED);
        Order order = request.getOrderItem().getOrder();
        String result = request.getStatus() == ServiceRequest.Status.APPROVED
                ? "approved" : "declined";
        String message = "Your " + request.getType().name().toLowerCase(Locale.ROOT)
                + " request for " + request.getOrderItem().getProductName() + " was " + result
                + (request.getReviewNotes() == null ? "." : ". Owner note: " + request.getReviewNotes());
        notificationService.notify(order.getUser(), order, fulfillment,
                "SERVICE_REQUEST_REVIEWED", "Return/exchange request " + result, message);
        notificationService.notify(request.getWarehouse().getOperator(), order, fulfillment,
                "SERVICE_REQUEST_REVIEWED", "Owner " + result + " a return/exchange request",
                "Order " + order.getOrderNumber() + " · " + request.getOrderItem().getProductName()
                        + " was " + result + "."
                        + (request.getStatus() == ServiceRequest.Status.APPROVED
                        ? " Assign an active warehouse agent for customer pickup." : ""));
        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto assignAgent(String email, Long requestId, Long agentId,
                                         boolean sellerRoute) {
        User operator = getUser(email);
        requireRole(operator, User.Role.WAREHOUSE_OPERATOR);
        WarehouseLocation warehouse = warehouseRepository.findByOperatorId(operator.getId())
                .filter(location -> Boolean.TRUE.equals(location.getActive()))
                .orElseThrow(() -> new AccessDeniedException(
                        "Your warehouse must be active before assigning service-request routes."));
        ServiceRequest request = getLockedRequest(requestId);
        if (request.getWarehouse() == null || !request.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AccessDeniedException("This return/exchange request is not assigned to your warehouse.");
        }
        ServiceRequest.Status required = sellerRoute
                ? ServiceRequest.Status.AT_WAREHOUSE : ServiceRequest.Status.APPROVED;
        if (request.getStatus() != required) {
            throw new ConflictException(sellerRoute
                    ? "The returned item must reach the warehouse before assigning its seller route."
                    : "The owner must approve this request before pickup can be assigned.");
        }
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getDeliveryAgentActive())
                        && candidate.getDeliveryWarehouse() != null
                        && candidate.getDeliveryWarehouse().getId().equals(warehouse.getId()))
                .orElseThrow(() -> new BadRequestException(
                        "Choose an active delivery agent registered under this warehouse."));
        request.setDeliveryAgent(agent);
        request.setStatus(sellerRoute ? ServiceRequest.Status.SELLER_ROUTE_ASSIGNED
                : ServiceRequest.Status.PICKUP_ASSIGNED);
        notificationService.notify(agent, request.getOrderItem().getOrder(),
                getFulfillment(request), "SERVICE_ROUTE_ASSIGNED", "Return/exchange route assigned",
                "Accept and complete the " + (sellerRoute ? "return-to-seller route" : "customer pickup route")
                        + " for order " + request.getOrderItem().getOrder().getOrderNumber() + ".");
        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto advanceByAgent(String email, Long requestId, ServiceRequest.Status stage) {
        User agent = getUser(email);
        requireRole(agent, User.Role.DELIVERY_AGENT);
        ServiceRequest request = getLockedRequest(requestId);
        if (request.getDeliveryAgent() == null || !request.getDeliveryAgent().getId().equals(agent.getId())
                || !Boolean.TRUE.equals(agent.getDeliveryAgentActive())) {
            throw new AccessDeniedException("This return/exchange route is not assigned to your account.");
        }
        ServiceRequest.Status expected = switch (request.getStatus()) {
            case PICKUP_ASSIGNED -> ServiceRequest.Status.PICKED_UP;
            case PICKED_UP -> ServiceRequest.Status.AT_WAREHOUSE;
            case SELLER_ROUTE_ASSIGNED -> ServiceRequest.Status.RECEIVED_BY_SELLER;
            case REPLACEMENT_ASSIGNED -> ServiceRequest.Status.COMPLETED;
            default -> throw new ConflictException("There is no delivery-agent stage available for this request.");
        };
        if (stage != expected) {
            throw new ConflictException("Submit only the next assigned return/exchange stage.");
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        request.setStatus(stage);
        switch (stage) {
            case PICKED_UP -> request.setPickedUpAt(now);
            case AT_WAREHOUSE -> request.setWarehouseReceivedAt(now);
            case RECEIVED_BY_SELLER -> request.setSellerReceivedAt(now);
            case COMPLETED -> request.setReplacementDeliveredAt(now);
            default -> throw new ConflictException("Invalid agent stage.");
        }
        Order order = request.getOrderItem().getOrder();
        SellerFulfillment fulfillment = getFulfillment(request);
        notificationService.notify(request.getWarehouse().getOperator(), order, fulfillment,
                "SERVICE_STAGE_UPDATED", "Return/exchange route updated",
                request.getOrderItem().getProductName() + " · " + stage.name().replace('_', ' '));
        if (stage == ServiceRequest.Status.AT_WAREHOUSE) {
            notificationService.notify(fulfillment.getOwner(), order, fulfillment,
                    "SERVICE_STAGE_UPDATED", "Returned item reached the warehouse",
                    "Assign an active agent to deliver the returned item to the seller.");
        } else if (stage == ServiceRequest.Status.RECEIVED_BY_SELLER) {
            notificationService.notify(fulfillment.getOwner(), order, fulfillment,
                    "SERVICE_SELLER_RECEIPT", "Returned item is with the seller",
                    "Confirm receipt in Operations Studio before refund or replacement.");
        } else if (stage == ServiceRequest.Status.COMPLETED) {
            notificationService.notify(order.getUser(), order, fulfillment,
                    "EXCHANGE_COMPLETED", "Replacement delivered",
                    "The replacement for " + request.getOrderItem().getProductName()
                            + " has been delivered successfully.");
        }
        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto confirmSellerReceipt(String email, Long requestId, String notes) {
        User owner = getUser(email);
        ServiceRequest request = getLockedRequest(requestId);
        SellerFulfillment fulfillment = getFulfillment(request);
        if (owner.getRole() != User.Role.ADMIN
                && (owner.getRole() != User.Role.OWNER || fulfillment.getOwner() == null
                || !fulfillment.getOwner().getId().equals(owner.getId()))) {
            throw new AccessDeniedException("Only this product's owner can confirm seller receipt.");
        }
        if (request.getStatus() != ServiceRequest.Status.RECEIVED_BY_SELLER) {
            throw new ConflictException("The returned item has not been delivered to the seller.");
        }
        if (notes != null && !notes.isBlank()) request.setReviewNotes(notes.trim());
        Order order = request.getOrderItem().getOrder();
        if (request.getType() == ServiceRequest.Type.RETURN) {
            request.setStatus(ServiceRequest.Status.COMPLETED);
            if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                order.setPaymentStatus(Order.PaymentStatus.REFUND_PENDING);
                userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN).forEach(admin ->
                        notificationService.notify(admin, order, fulfillment, "RETURN_REFUND_REVIEW",
                                "Return received; refund review required",
                                "The owner confirmed return receipt for order " + order.getOrderNumber()
                                        + ". Review refund for the returned item."));
            }
        } else {
            request.setStatus(ServiceRequest.Status.REPLACEMENT_READY);
            notificationService.notify(request.getWarehouse().getOperator(), order, fulfillment,
                    "REPLACEMENT_READY", "Replacement is ready for delivery",
                    "Assign an active agent at " + request.getWarehouse().getName()
                            + " to deliver the replacement to the customer.");
        }
        notificationService.notify(order.getUser(), order, fulfillment,
                "RETURN_RECEIVED_BY_SELLER", "Seller confirmed return receipt",
                request.getType() == ServiceRequest.Type.RETURN
                        ? "The seller received your returned item. Any applicable refund is under review."
                        : "The seller received your old item and is preparing the replacement.");
        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto assignReplacementAgent(String email, Long requestId, Long agentId) {
        User operator = getUser(email);
        requireRole(operator, User.Role.WAREHOUSE_OPERATOR);
        WarehouseLocation warehouse = warehouseRepository.findByOperatorId(operator.getId())
                .filter(location -> Boolean.TRUE.equals(location.getActive()))
                .orElseThrow(() -> new AccessDeniedException("Your warehouse must be active."));
        ServiceRequest request = getLockedRequest(requestId);
        if (request.getStatus() != ServiceRequest.Status.REPLACEMENT_READY
                || request.getWarehouse() == null
                || !request.getWarehouse().getId().equals(warehouse.getId())) {
            throw new ConflictException("This replacement is not ready for assignment at your warehouse.");
        }
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getDeliveryAgentActive())
                        && candidate.getDeliveryWarehouse() != null
                        && candidate.getDeliveryWarehouse().getId().equals(warehouse.getId()))
                .orElseThrow(() -> new BadRequestException(
                        "Choose an active delivery agent registered under this warehouse."));
        request.setDeliveryAgent(agent);
        request.setStatus(ServiceRequest.Status.REPLACEMENT_ASSIGNED);
        notificationService.notify(agent, request.getOrderItem().getOrder(), getFulfillment(request),
                "REPLACEMENT_ROUTE_ASSIGNED", "Replacement delivery assigned",
                "Deliver the approved replacement for order "
                        + request.getOrderItem().getOrder().getOrderNumber() + " to the customer.");
        return toDto(request);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forOrder(Long orderId) {
        return requestRepository.findByOrderItem_Order_IdOrderByRequestedAtDesc(orderId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forFulfillment(Long fulfillmentId) {
        return requestRepository.findByOrderItem_Fulfillment_IdOrderByRequestedAtDesc(fulfillmentId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forAgent(Long agentId) {
        return requestRepository.findByDeliveryAgent_IdOrderByRequestedAtDesc(agentId).stream()
                .filter(request -> request.getStatus() != ServiceRequest.Status.COMPLETED
                        && request.getStatus() != ServiceRequest.Status.REJECTED)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forAgent(String email) {
        User agent = getUser(email);
        requireRole(agent, User.Role.DELIVERY_AGENT);
        return forAgent(agent.getId());
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forWarehouse(Long warehouseId) {
        return requestRepository.findByWarehouse_IdOrderByRequestedAtAsc(warehouseId).stream()
                .filter(request -> request.getStatus() != ServiceRequest.Status.COMPLETED
                        && request.getStatus() != ServiceRequest.Status.REJECTED)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> forWarehouseOperator(String email) {
        User operator = getUser(email);
        requireRole(operator, User.Role.WAREHOUSE_OPERATOR);
        WarehouseLocation warehouse = warehouseRepository.findByOperatorId(operator.getId())
                .filter(location -> Boolean.TRUE.equals(location.getActive()))
                .orElseThrow(() -> new AccessDeniedException("Your warehouse must be active."));
        return forWarehouse(warehouse.getId());
    }

    @Transactional(readOnly = true)
    public String evidenceFileName(String email, Long requestId, String kind) {
        User viewer = getUser(email);
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request", requestId));
        SellerFulfillment fulfillment = getFulfillment(request);
        boolean authorized = viewer.getRole() == User.Role.ADMIN
                || (viewer.getRole() == User.Role.USER && request.getOrderItem().getOrder()
                        .getUser().getId().equals(viewer.getId()))
                || (viewer.getRole() == User.Role.OWNER && fulfillment.getOwner() != null
                        && fulfillment.getOwner().getId().equals(viewer.getId()))
                || (viewer.getRole() == User.Role.DELIVERY_AGENT && request.getDeliveryAgent() != null
                        && request.getDeliveryAgent().getId().equals(viewer.getId()))
                || (viewer.getRole() == User.Role.WAREHOUSE_OPERATOR && request.getWarehouse() != null
                        && request.getWarehouse().getOperator() != null
                        && request.getWarehouse().getOperator().getId().equals(viewer.getId()));
        if (!authorized) {
            throw new AccessDeniedException("You are not authorized to view this return evidence.");
        }
        String evidenceUrl;
        if ("photo".equalsIgnoreCase(kind)) {
            evidenceUrl = request.getPhotoUrl();
        } else if ("video".equalsIgnoreCase(kind)) {
            evidenceUrl = request.getVideoUrl();
        } else {
            throw new BadRequestException("Evidence type must be photo or video.");
        }
        validateEvidenceUrl(evidenceUrl, "video".equalsIgnoreCase(kind));
        return evidenceUrl.substring(evidenceUrl.indexOf("/uploads/order-evidence/")
                + "/uploads/order-evidence/".length());
    }

    private ServiceRequest toDtoEntity(Long id) {
        return requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service request", id));
    }

    private ServiceRequest getLockedRequest(Long id) {
        return toDtoEntity(id);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private SellerFulfillment getFulfillment(ServiceRequest request) {
        SellerFulfillment fulfillment = request.getOrderItem().getFulfillment();
        if (fulfillment == null) throw new ConflictException("The item's seller fulfillment is unavailable.");
        return fulfillment;
    }

    private void requireRole(User user, User.Role role) {
        if (user.getRole() != role) throw new AccessDeniedException("This action is not available for your role.");
    }

    private void validateEvidenceUrl(String url, boolean video) {
        String marker = "/uploads/order-evidence/";
        int markerIndex = url.indexOf(marker);
        if (markerIndex < 0) throw new BadRequestException("Upload the required return evidence first.");
        String fileName = url.substring(markerIndex + marker.length());
        String pattern = video ? "^[a-fA-F0-9-]+\\.mp4$"
                : "^[a-fA-F0-9-]+\\.(jpg|png|webp)$";
        if (!fileName.matches(pattern)) throw new BadRequestException("Invalid return evidence file.");
    }

    private ServiceRequestDto toDto(ServiceRequest request) {
        OrderItem item = request.getOrderItem();
        Order order = item.getOrder();
        SellerFulfillment fulfillment = item.getFulfillment();
        WarehouseLocation warehouse = request.getWarehouse();
        User agent = request.getDeliveryAgent();
        return ServiceRequestDto.builder()
                .id(request.getId())
                .orderItemId(item.getId())
                .fulfillmentId(fulfillment == null ? null : fulfillment.getId())
                .orderNumber(order.getOrderNumber())
                .productName(item.getProductName())
                .type(request.getType())
                .reason(request.getReason())
                .details(request.getDetails())
                .photoUrl("/service-requests/" + request.getId() + "/evidence/photo")
                .videoUrl("/service-requests/" + request.getId() + "/evidence/video")
                .status(request.getStatus())
                .reviewNotes(request.getReviewNotes())
                .warehouseId(warehouse == null ? null : warehouse.getId())
                .warehouseName(warehouse == null ? null : warehouse.getName())
                .warehouseStreet(warehouse == null ? null : warehouse.getStreet())
                .warehouseCity(warehouse == null ? null : warehouse.getCity())
                .warehousePostalCode(warehouse == null ? null : warehouse.getPostalCode())
                .warehouseLatitude(warehouse == null ? null : warehouse.getLatitude())
                .warehouseLongitude(warehouse == null ? null : warehouse.getLongitude())
                .deliveryAgentId(agent == null ? null : agent.getId())
                .deliveryAgentName(agent == null ? null : agent.getFullName())
                .customerName(order.getAddressFullName())
                .customerStreet(order.getAddressStreet())
                .customerCity(order.getAddressCity())
                .customerPostalCode(order.getAddressPostalCode())
                .customerLatitude(order.getAddressLatitude())
                .customerLongitude(order.getAddressLongitude())
                .sellerName(fulfillment == null || fulfillment.getOwner() == null
                        ? "9Tails Store" : fulfillment.getOwner().getFullName())
                .sellerStreet(fulfillment == null ? null : fulfillment.getPickupStreet())
                .sellerCity(fulfillment == null ? null : fulfillment.getPickupCity())
                .sellerPostalCode(fulfillment == null ? null : fulfillment.getPickupPostalCode())
                .sellerLatitude(fulfillment == null || fulfillment.getPickupLocation() == null
                        ? null : fulfillment.getPickupLocation().getLatitude())
                .sellerLongitude(fulfillment == null || fulfillment.getPickupLocation() == null
                        ? null : fulfillment.getPickupLocation().getLongitude())
                .requestedAt(request.getRequestedAt())
                .pickedUpAt(request.getPickedUpAt())
                .warehouseReceivedAt(request.getWarehouseReceivedAt())
                .sellerReceivedAt(request.getSellerReceivedAt())
                .replacementDeliveredAt(request.getReplacementDeliveredAt())
                .build();
    }
}
