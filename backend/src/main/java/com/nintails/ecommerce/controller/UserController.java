package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.UserRoleUpdateRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.repository.WarehouseLocationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        data.put("role", user.getRole().name());
        data.put("deliveryAgentActive", Boolean.TRUE.equals(user.getDeliveryAgentActive()));
        data.put("deliveryWarehouseId", user.getDeliveryWarehouse() == null
                ? null : user.getDeliveryWarehouse().getId());
        data.put("deliveryWarehouseName", user.getDeliveryWarehouse() == null
                ? null : user.getDeliveryWarehouse().getName());
        data.put("warehouseActive", user.getRole() == User.Role.WAREHOUSE_OPERATOR
                && warehouseLocationRepository.findByOperatorId(user.getId())
                .map(warehouse -> Boolean.TRUE.equals(warehouse.getActive()))
                .orElse(false));
        data.put("phoneNumber", user.getPhoneNumber());
        data.put("phoneVerified", user.getPhoneVerified());
        data.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // GET /api/users  [ADMIN]
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Object>> getAllUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", user.getId());
                    data.put("fullName", user.getFullName());
                    data.put("email", user.getEmail());
                    data.put("role", user.getRole().name());
                    data.put("deliveryWarehouseId", user.getDeliveryWarehouse() == null
                            ? null : user.getDeliveryWarehouse().getId());
                    data.put("deliveryWarehouseName", user.getDeliveryWarehouse() == null
                            ? null : user.getDeliveryWarehouse().getName());
                    return data;
                }).toList();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getRole() == User.Role.ADMIN || request.getRole() == User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Administrator role changes must use the administrator account workflow."));
        }
        user.setRole(request.getRole());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("User role updated", null));
    }
}
