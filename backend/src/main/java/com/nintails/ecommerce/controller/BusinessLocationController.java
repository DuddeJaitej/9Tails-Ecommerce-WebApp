package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.BusinessLocationDto;
import com.nintails.ecommerce.dto.BusinessLocationRequest;
import com.nintails.ecommerce.service.BusinessLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/business-locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class BusinessLocationController {

    private final BusinessLocationService businessLocationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessLocationDto>>> list(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(businessLocationService.list(user.getUsername())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BusinessLocationDto>> create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody BusinessLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Business location saved",
                businessLocationService.create(user.getUsername(), request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        businessLocationService.delete(user.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.ok("Business location deleted", null));
    }
}
