package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.DeliveryAgentActivationRequest;
import com.nintails.ecommerce.dto.DeliveryAgentDto;
import com.nintails.ecommerce.service.DeliveryRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouse/delivery-agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
public class OwnerDeliveryAgentController {

    private final DeliveryRegistrationService registrationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryAgentDto>>> list(
            @AuthenticationPrincipal UserDetails owner) {
        return ResponseEntity.ok(ApiResponse.ok(
                registrationService.getAgentsForWarehouse(owner.getUsername())));
    }

    @PatchMapping("/{id}/activation")
    public ResponseEntity<ApiResponse<DeliveryAgentDto>> setActivation(
            @AuthenticationPrincipal UserDetails owner,
            @PathVariable Long id,
            @Valid @RequestBody DeliveryAgentActivationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Delivery agent account status updated",
                registrationService.setAgentActive(owner.getUsername(), id, request.getActive())));
    }
}
