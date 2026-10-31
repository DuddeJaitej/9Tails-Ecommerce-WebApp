package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.BusinessLocationDto;
import com.nintails.ecommerce.service.BusinessLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/business-locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBusinessLocationController {

    private final BusinessLocationService businessLocationService;

    @GetMapping("/owners/{ownerId}")
    public ResponseEntity<ApiResponse<List<BusinessLocationDto>>> listForOwner(
            @PathVariable Long ownerId) {
        return ResponseEntity.ok(ApiResponse.ok(
                businessLocationService.listForAdministrator(ownerId)));
    }
}
