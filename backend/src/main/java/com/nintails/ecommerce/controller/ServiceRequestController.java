package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.ServiceRequestAgentAssignment;
import com.nintails.ecommerce.dto.ServiceRequestDto;
import com.nintails.ecommerce.dto.ServiceRequestReviewRequest;
import com.nintails.ecommerce.dto.ServiceRequestStageRequest;
import com.nintails.ecommerce.service.OrderEvidenceStorageService;
import com.nintails.ecommerce.service.ServiceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/service-requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService service;
    private final OrderEvidenceStorageService evidenceStorageService;

    @GetMapping("/warehouse")
    @PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
    public ResponseEntity<ApiResponse<List<ServiceRequestDto>>> warehouseRequests(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(service.forWarehouseOperator(user.getUsername())));
    }

    @GetMapping("/agent")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<List<ServiceRequestDto>>> agentRequests(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(service.forAgent(user.getUsername())));
    }

    @GetMapping("/{id}/evidence/{kind}")
    public ResponseEntity<Resource> evidence(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @PathVariable String kind) {
        String fileName = service.evidenceFileName(user.getUsername(), id, kind);
        MediaType contentType = fileName.endsWith(".mp4") ? MediaType.parseMediaType("video/mp4")
                : fileName.endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName).build().toString())
                .body(evidenceStorageService.load(fileName));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> review(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Customer service request reviewed",
                service.review(user.getUsername(), id, request.getDecision(), request.getNotes())));
    }

    @PatchMapping("/{id}/pickup-assignment")
    @PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> assignPickup(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestAgentAssignment request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Return pickup agent assigned",
                service.assignAgent(user.getUsername(), id, request.getDeliveryAgentId(), false)));
    }

    @PatchMapping("/{id}/seller-assignment")
    @PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> assignSellerRoute(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestAgentAssignment request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Return-to-seller agent assigned",
                service.assignAgent(user.getUsername(), id, request.getDeliveryAgentId(), true)));
    }

    @PatchMapping("/{id}/replacement-assignment")
    @PreAuthorize("hasRole('WAREHOUSE_OPERATOR')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> assignReplacement(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestAgentAssignment request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Replacement delivery agent assigned",
                service.assignReplacementAgent(user.getUsername(), id, request.getDeliveryAgentId())));
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> advance(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestStageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Return/exchange delivery stage recorded",
                service.advanceByAgent(user.getUsername(), id, request.getStage())));
    }

    @PatchMapping("/{id}/seller-receipt")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRequestDto>> confirmSellerReceipt(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @RequestBody(required = false) ServiceRequestReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Seller receipt confirmed",
                service.confirmSellerReceipt(user.getUsername(), id,
                        request == null ? null : request.getNotes())));
    }
}
