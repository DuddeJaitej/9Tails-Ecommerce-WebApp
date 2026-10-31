package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.WarehouseLocationDto;
import com.nintails.ecommerce.service.WarehouseLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWarehouseLocationController {

    private final WarehouseLocationService warehouseLocationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(warehouseLocationService.listAll()));
    }

    @PatchMapping("/{id}/activation")
    public ResponseEntity<ApiResponse<WarehouseLocationDto>> setActivation(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {
        Boolean active = request.get("active");
        if (active == null) {
            throw new com.nintails.ecommerce.exception.BadRequestException(
                    "Specify whether the warehouse location should be active.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Warehouse location status updated",
                warehouseLocationService.setActive(id, active)));
    }
}
