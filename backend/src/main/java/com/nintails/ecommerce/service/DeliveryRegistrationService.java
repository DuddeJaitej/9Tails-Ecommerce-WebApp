package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AuthResponse;
import com.nintails.ecommerce.dto.DeliveryAgentDto;
import com.nintails.ecommerce.dto.DeliveryRegistrationRequest;
import com.nintails.ecommerce.dto.WarehouseLocationDto;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.WarehouseLocation;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.repository.WarehouseLocationRepository;
import com.nintails.ecommerce.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryRegistrationService {

    private final WarehouseLocationRepository warehouseLocationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public java.util.List<WarehouseLocationDto> getAvailableWarehouses() {
        return warehouseLocationRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(location -> WarehouseLocationDto.builder()
                        .id(location.getId())
                        .name(location.getName())
                        .street(location.getStreet())
                        .city(location.getCity())
                        .postalCode(location.getPostalCode())
                        .latitude(location.getLatitude())
                        .longitude(location.getLongitude())
                        .active(true)
                        .build())
                .toList();
    }

    @Transactional
    public AuthResponse register(DeliveryRegistrationRequest request) {
        User.Role role = request.getRole();
        WarehouseLocation deliveryWarehouse = null;

        if (role == User.Role.DELIVERY_AGENT) {
            if (request.getDeliveryWarehouseId() == null) {
                throw new BadRequestException("Choose an approved warehouse to work with.");
            }
            deliveryWarehouse = warehouseLocationRepository
                    .findByIdAndActiveTrue(request.getDeliveryWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Approved warehouse", request.getDeliveryWarehouseId()));
        } else if (role == User.Role.WAREHOUSE_OPERATOR) {
            validateWarehouseApplication(request);
            if (request.getDeliveryWarehouseId() != null) {
                throw new BadRequestException(
                        "A new warehouse registration cannot be attached to an existing warehouse.");
            }
        } else {
            throw new BadRequestException(
                    "Only delivery-agent and warehouse-operator accounts can register here.");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account already exists for this email address");
        }
        User user = userRepository.save(Objects.requireNonNull(User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .deliveryWarehouse(deliveryWarehouse)
                .deliveryAgentActive(false)
                .build()));
        if (role == User.Role.WAREHOUSE_OPERATOR) {
            WarehouseLocation warehouse = warehouseLocationRepository.save(WarehouseLocation.builder()
                    .name(request.getWarehouseName().trim())
                    .street(request.getWarehouseStreet().trim())
                    .city(request.getWarehouseCity().trim())
                    .state(request.getWarehouseState().trim())
                    .postalCode(request.getWarehousePostalCode())
                    .latitude(request.getWarehouseLatitude())
                    .longitude(request.getWarehouseLongitude())
                    .operator(user)
                    .active(false)
                    .build());
            notificationService.notifyAdmins(userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN),
                    null, null, "WAREHOUSE_APPROVAL_REQUIRED",
                    "Warehouse registration awaiting approval",
                    user.getFullName() + " registered warehouse " + warehouse.getName()
                            + " in " + warehouse.getCity() + ". Review its location in the admin dashboard.");
        } else {
            notificationService.notify(deliveryWarehouse.getOperator(), null, null,
                    "DELIVERY_AGENT_APPROVAL_REQUIRED", "New delivery agent awaiting approval",
                    user.getFullName() + " registered for " + deliveryWarehouse.getName()
                            + ". Review the account in your warehouse workspace.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        String token = jwtUtils.generateToken((UserDetails) authentication.getPrincipal());
        log.info("Delivery account {} registered with role {}.",
                user.getId(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<DeliveryAgentDto> getAgentsForWarehouse(String operatorEmail) {
        WarehouseLocation warehouse = getActiveWarehouseForOperator(operatorEmail);
        return userRepository.findByDeliveryWarehouse_IdOrderByFullNameAsc(warehouse.getId()).stream()
                .filter(agent -> agent.getRole() == User.Role.DELIVERY_AGENT)
                .map(this::toAgentDto)
                .toList();
    }

    @Transactional
    public DeliveryAgentDto setAgentActive(String operatorEmail, Long agentId, boolean active) {
        WarehouseLocation warehouse = getActiveWarehouseForOperator(operatorEmail);
        User agent = userRepository.findByIdAndRole(agentId, User.Role.DELIVERY_AGENT)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent", agentId));
        if (agent.getDeliveryWarehouse() == null
                || !agent.getDeliveryWarehouse().getId().equals(warehouse.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only manage delivery agents registered under your warehouse.");
        }
        agent.setDeliveryAgentActive(active);
        User savedAgent = userRepository.save(agent);
        notificationService.notify(savedAgent, null, null,
                active ? "DELIVERY_AGENT_APPROVED" : "DELIVERY_AGENT_DEACTIVATED",
                active ? "Your delivery account is active" : "Your delivery account is inactive",
                active ? "Your warehouse operator approved your account. You can now receive delivery tasks."
                        : "Your warehouse operator deactivated your account. Contact the warehouse operator for details.");
        return toAgentDto(savedAgent);
    }

    private DeliveryAgentDto toAgentDto(User agent) {
        WarehouseLocation warehouse = agent.getDeliveryWarehouse();
        return DeliveryAgentDto.builder()
                .id(agent.getId())
                .fullName(agent.getFullName())
                .email(agent.getEmail())
                .warehouseId(warehouse == null ? null : warehouse.getId())
                .warehouseName(warehouse == null ? null : warehouse.getName())
                .active(Boolean.TRUE.equals(agent.getDeliveryAgentActive()))
                .build();
    }

    private WarehouseLocation getActiveWarehouseForOperator(String email) {
        User operator = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (operator.getRole() != User.Role.WAREHOUSE_OPERATOR) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A warehouse-operator account is required.");
        }
        WarehouseLocation warehouse = warehouseLocationRepository.findByOperatorId(operator.getId())
                .orElseThrow(() -> new ConflictException(
                        "No warehouse is registered for this operator."));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ConflictException("The administrator must approve your warehouse before operations begin.");
        }
        return warehouse;
    }

    private void validateWarehouseApplication(DeliveryRegistrationRequest request) {
        if (request.getWarehouseName() == null || request.getWarehouseName().isBlank()
                || request.getWarehouseStreet() == null || request.getWarehouseStreet().isBlank()
                || request.getWarehouseCity() == null || request.getWarehouseCity().isBlank()
                || request.getWarehouseState() == null || request.getWarehouseState().isBlank()
                || request.getWarehousePostalCode() == null
                || request.getWarehouseLatitude() == null
                || request.getWarehouseLongitude() == null) {
            throw new BadRequestException(
                    "Warehouse name, address, valid postal code, and map coordinates are required.");
        }
    }

}
