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
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryWorkflowService workflowService;

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<FulfillmentDto>>> tasks(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getDeliveryTasks(user.getUsername())));
    }

    @GetMapping("/offers")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<FulfillmentDto>>> offers(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.refreshDeliveryOffers(user.getUsername())));
    }

    @GetMapping("/warehouse-tasks")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<FulfillmentDto>>> warehouseTasks(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getWarehouseTasks(user.getUsername())));
    }

    @GetMapping("/warehouse-arrival-qr")
    @PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
    public ResponseEntity<ApiResponse<WarehouseQrDto>> warehouseArrivalQr(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.getWarehouseArrivalQr(user.getUsername())));
    }

    @PostMapping("/warehouse-arrival-tasks")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<List<FulfillmentDto>>> warehouseArrivalTasks(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody QrScanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.getWarehouseArrivalTasks(user.getUsername(), request.getToken())));
    }

    @PostMapping("/warehouse-arrival")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<StageResultDto>> confirmWarehouseArrival(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody WarehouseArrivalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse arrival confirmed",
                workflowService.confirmWarehouseArrival(user.getUsername(), request)));
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<DeliveryScanResultDto>> scan(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody QrScanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.validateQr(user.getUsername(), request.getToken())));
    }

    @PostMapping("/fulfillments/{id}/qr/{stage}/replace")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> replaceQrToken(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable Stage stage) {
        return ResponseEntity.ok(ApiResponse.ok("QR token replaced",
                Map.of("token", workflowService.replaceQrToken(user.getUsername(), id, stage))));
    }

    @PostMapping("/fulfillments/{id}/assignment")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> assignAgent(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AssignDeliveryAgentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Delivery agent assigned",
                workflowService.assignDeliveryAgent(user.getUsername(), id,
                        request.getDeliveryAgentId())));
    }

    @PostMapping("/fulfillments/{id}/transfer-assignment")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> assignTransferAgent(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AssignDeliveryAgentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse transfer agent offered the route",
                workflowService.assignTransferDeliveryAgent(user.getUsername(), id,
                        request.getDeliveryAgentId())));
    }

    @PostMapping("/fulfillments/{id}/warehouse-acceptance")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> acceptWarehousePickup(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse accepted pickup",
                workflowService.acceptWarehousePickup(user.getUsername(), id)));
    }

    @PostMapping("/fulfillments/{id}/warehouse-rejection")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> rejectWarehousePickup(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody WarehousePickupDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse decision recorded",
                workflowService.rejectWarehousePickup(
                        user.getUsername(), id, request.getReason())));
    }

    @PostMapping("/fulfillments/{id}/final-assignment")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> assignFinalAgent(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AssignDeliveryAgentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Final delivery agent offered the route",
                workflowService.assignFinalDeliveryAgent(user.getUsername(), id,
                        request.getDeliveryAgentId())));
    }

    @PostMapping("/fulfillments/{id}/accept")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> acceptOffer(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Delivery offer accepted",
                workflowService.acceptDeliveryOffer(user.getUsername(), id)));
    }

    @PostMapping("/pickup")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','ADMIN')")
    public ResponseEntity<ApiResponse<StageResultDto>> pickup(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Pickup confirmed",
                workflowService.submitStage(user.getUsername(), request.getToken(), request,
                        Stage.PICKUP_FROM_OWNER)));
    }

    @PostMapping("/warehouse-receipt")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<StageResultDto>> warehouseReceipt(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse receipt confirmed",
                workflowService.submitStage(user.getUsername(), request.getToken(), request,
                        Stage.WAREHOUSE_RECEIVED)));
    }

    @PostMapping("/warehouse-dispatch")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<StageResultDto>> warehouseDispatch(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Package dispatched to the destination warehouse",
                workflowService.submitStage(user.getUsername(), request.getToken(), request,
                        Stage.OUT_FOR_DELIVERY_TO_STORE)));
    }

    @PostMapping("/warehouse-destination-receipt")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<StageResultDto>> destinationWarehouseReceipt(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Package received at the destination warehouse",
                workflowService.submitStage(user.getUsername(), request.getToken(), request,
                        Stage.STORE_RECEIVED)));
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','ADMIN')")
    public ResponseEntity<ApiResponse<StageResultDto>> dispatch(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order is out for delivery",
                workflowService.submitStage(user.getUsername(), request.getToken(), request,
                        Stage.OUT_FOR_DELIVERY)));
    }

    @PostMapping("/customer-confirmation-request")
    @PreAuthorize("hasAnyRole('DELIVERY_AGENT','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> requestCustomerConfirmation(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody StageSubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Customer confirmation requested",
                workflowService.requestCustomerDeliveryConfirmation(
                        user.getUsername(), request.getToken(), request.getNotes())));
    }

    @PostMapping("/customer/fulfillments/{id}/confirmation")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentDto>> confirmCustomerDelivery(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CustomerDeliveryConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Delivery response recorded",
                workflowService.confirmCustomerDelivery(user.getUsername(), id, request)));
    }

    @GetMapping("/fulfillments/{id}/history")
    public ResponseEntity<ApiResponse<List<DeliveryEventDto>>> history(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.getHistory(user.getUsername(), id)));
    }

    @GetMapping("/admin-events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DeliveryEventDto>>> adminEvents(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                workflowService.getRecentAdminDeliveryEvents(user.getUsername())));
    }
}
