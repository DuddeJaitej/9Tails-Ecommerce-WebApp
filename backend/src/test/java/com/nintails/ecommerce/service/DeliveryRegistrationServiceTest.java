package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.DeliveryRegistrationRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.WarehouseLocation;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.repository.WarehouseLocationRepository;
import com.nintails.ecommerce.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryRegistrationServiceTest {

    @Mock private WarehouseLocationRepository warehouseLocationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private NotificationService notificationService;
    @Mock private Authentication authentication;
    @InjectMocks private DeliveryRegistrationService registrationService;

    @Test
    void warehouseOperatorCanSelfRegisterPendingLocationForAdminApproval() {
        when(userRepository.existsByEmail("worker@example.com")).thenReturn(false);
        when(userRepository.findByRoleOrderByFullNameAsc(User.Role.ADMIN)).thenReturn(java.util.List.of());
        when(passwordEncoder.encode("a-secure-password-123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(warehouseLocationRepository.save(any(WarehouseLocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        configureAuthentication();

        var result = registrationService.register(warehouseRegistrationRequest());

        assertEquals("WAREHOUSE_OPERATOR", result.getRole());
        assertEquals("session-token", result.getToken());
        verify(warehouseLocationRepository).save(org.mockito.ArgumentMatchers.argThat(warehouse ->
                warehouse.getOperator().getId().equals(42L)
                        && Boolean.FALSE.equals(warehouse.getActive())
                        && warehouse.getLatitude().equals(17.385)));
    }

    @Test
    void deliveryAgentCanRegisterOnlyUnderAnApprovedWarehouse() {
        WarehouseLocation warehouse = WarehouseLocation.builder()
                .id(7L).name("North Hub").active(true)
                .operator(User.builder().id(10L).role(User.Role.WAREHOUSE_OPERATOR).build())
                .build();
        when(warehouseLocationRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(warehouse));
        when(userRepository.existsByEmail("worker@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(passwordEncoder.encode("a-secure-password-123")).thenReturn("encoded");
        configureAuthentication();

        var result = registrationService.register(agentRegistrationRequest(7L));

        assertEquals("DELIVERY_AGENT", result.getRole());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(agent ->
                agent.getRole() == User.Role.DELIVERY_AGENT
                        && agent.getDeliveryWarehouse().getId().equals(7L)
                        && Boolean.FALSE.equals(agent.getDeliveryAgentActive())));
        verify(notificationService).notify(
                org.mockito.ArgumentMatchers.eq(warehouse.getOperator()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("DELIVERY_AGENT_APPROVAL_REQUIRED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deliveryAgentRegistrationRejectsUnapprovedWarehouse() {
        when(warehouseLocationRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> registrationService.register(agentRegistrationRequest(7L)));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void warehouseOperatorCanApproveAnAgentRegisteredToTheirWarehouse() {
        User operator = User.builder().id(10L).email("operator@example.com")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        WarehouseLocation warehouse = WarehouseLocation.builder()
                .id(7L).name("North Hub").active(true).operator(operator).build();
        User agent = User.builder().id(42L).email("worker@example.com")
                .fullName("Delivery Worker").role(User.Role.DELIVERY_AGENT)
                .deliveryWarehouse(warehouse).deliveryAgentActive(false).build();
        when(userRepository.findByEmail(operator.getEmail())).thenReturn(Optional.of(operator));
        when(warehouseLocationRepository.findByOperatorId(operator.getId()))
                .thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(agent));
        when(userRepository.save(agent)).thenReturn(agent);

        var result = registrationService.setAgentActive(operator.getEmail(), agent.getId(), true);

        assertTrue(result.isActive());
        verify(notificationService).notify(
                org.mockito.ArgumentMatchers.eq(agent),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("DELIVERY_AGENT_APPROVED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void warehouseOperatorCannotApproveAnAgentFromAnotherWarehouse() {
        User operator = User.builder().id(10L).email("operator@example.com")
                .role(User.Role.WAREHOUSE_OPERATOR).build();
        WarehouseLocation ownWarehouse = WarehouseLocation.builder()
                .id(7L).name("North Hub").active(true).operator(operator).build();
        WarehouseLocation otherWarehouse = WarehouseLocation.builder()
                .id(8L).name("South Hub").active(true).build();
        User agent = User.builder().id(42L).email("worker@example.com")
                .role(User.Role.DELIVERY_AGENT).deliveryWarehouse(otherWarehouse)
                .deliveryAgentActive(false).build();
        when(userRepository.findByEmail(operator.getEmail())).thenReturn(Optional.of(operator));
        when(warehouseLocationRepository.findByOperatorId(operator.getId()))
                .thenReturn(Optional.of(ownWarehouse));
        when(userRepository.findByIdAndRole(agent.getId(), User.Role.DELIVERY_AGENT))
                .thenReturn(Optional.of(agent));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> registrationService.setAgentActive(operator.getEmail(), agent.getId(), true));
        verify(userRepository, never()).save(agent);
        verify(notificationService, never()).notify(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void warehouseRegistrationRequiresLocationCoordinates() {
        DeliveryRegistrationRequest request = warehouseRegistrationRequest();
        request.setWarehouseLatitude(null);

        assertThrows(BadRequestException.class, () -> registrationService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    private void configureAuthentication() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("worker@example.com")
                        .password("encoded")
                        .authorities("ROLE_DELIVERY_AGENT", "ROLE_WAREHOUSE_OPERATOR")
                        .build());
        when(jwtUtils.generateToken(any(UserDetails.class))).thenReturn("session-token");
    }

    private DeliveryRegistrationRequest warehouseRegistrationRequest() {
        DeliveryRegistrationRequest request = baseRequest();
        request.setRole(User.Role.WAREHOUSE_OPERATOR);
        request.setWarehouseName("North Hub");
        request.setWarehouseStreet("1 Market Street");
        request.setWarehouseCity("Hyderabad");
        request.setWarehouseState("Telangana");
        request.setWarehousePostalCode("500001");
        request.setWarehouseLatitude(17.385);
        request.setWarehouseLongitude(78.4867);
        return request;
    }

    private DeliveryRegistrationRequest agentRegistrationRequest(Long warehouseId) {
        DeliveryRegistrationRequest request = baseRequest();
        request.setRole(User.Role.DELIVERY_AGENT);
        request.setDeliveryWarehouseId(warehouseId);
        return request;
    }

    private DeliveryRegistrationRequest baseRequest() {
        DeliveryRegistrationRequest request = new DeliveryRegistrationRequest();
        request.setFullName("Delivery Worker");
        request.setEmail(" WORKER@EXAMPLE.COM ");
        request.setPassword("a-secure-password-123");
        return request;
    }
}
