package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.*;
import com.nintails.ecommerce.entity.DeliveryStageToken.Stage;
import com.nintails.ecommerce.service.DeliveryWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/owner/fulfillments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class OwnerFulfillmentController {

    private final DeliveryWorkflowService workflowService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FulfillmentDto>>> list(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getOwnerTasks(user.getUsername())));
    }

    @PatchMapping("/{id}/pickup-location")
    public ResponseEntity<ApiResponse<FulfillmentDto>> setPickupLocation(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody OwnerPickupLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Pickup location assigned",
                workflowService.setPickupLocation(user.getUsername(), id, request.getBusinessLocationId())));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<StageResultDto>> confirm(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Order confirmed",
                workflowService.confirmOrder(user.getUsername(), id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<FulfillmentDto>> reject(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody OwnerDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order rejected",
                workflowService.rejectOrder(user.getUsername(), id, request.getReason())));
    }

    @GetMapping("/{id}/qr/{stage}")
    public ResponseEntity<ApiResponse<OwnerPickupQrTokenDto>> getQrToken(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable Stage stage) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.getQrToken(user.getUsername(), id, stage)));
    }
}
