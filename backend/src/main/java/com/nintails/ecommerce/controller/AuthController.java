package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.dto.AdminAccountDto;
import com.nintails.ecommerce.dto.AdminAccountCreateRequest;
import com.nintails.ecommerce.dto.AdminPasswordResetEmailRequest;
import com.nintails.ecommerce.dto.AdminPasswordResetRequest;
import com.nintails.ecommerce.dto.AuthResponse;
import com.nintails.ecommerce.dto.WarehouseLocationDto;
import com.nintails.ecommerce.dto.GoogleLoginRequest;
import com.nintails.ecommerce.dto.LoginRequest;
import com.nintails.ecommerce.dto.OwnerRegistrationRequest;
import com.nintails.ecommerce.dto.RegisterRequest;
import com.nintails.ecommerce.dto.DeliveryRegistrationRequest;
import com.nintails.ecommerce.service.AdminAccountService;
import com.nintails.ecommerce.service.AuthService;
import com.nintails.ecommerce.service.DeliveryRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AdminAccountService adminAccountService;
    private final DeliveryRegistrationService deliveryRegistrationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registered successfully", response));
    }

    @PostMapping("/delivery/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerDeliveryWorker(
            @Valid @RequestBody DeliveryRegistrationRequest request) {
        AuthResponse response = deliveryRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Delivery account registered", response));
    }

    @GetMapping("/delivery/warehouses")
    public ResponseEntity<ApiResponse<java.util.List<WarehouseLocationDto>>> deliveryWarehouses() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryRegistrationService.getAvailableWarehouses()));
    }

    @PostMapping("/owner/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerOwner(
            @Valid @RequestBody OwnerRegistrationRequest request) {
        AuthResponse response = authService.registerOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Business owner account created", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/operations/login")
    public ResponseEntity<ApiResponse<AuthResponse>> operationsLogin(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginOperations(request);
        return ResponseEntity.ok(ApiResponse.ok("Operations sign-in successful", response));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Admin login successful", response));
    }

    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminAccountDto>> registerAdmin(
            @AuthenticationPrincipal UserDetails administrator,
            @Valid @RequestBody AdminAccountCreateRequest request) {
        AdminAccountDto account = adminAccountService.registerAdmin(
                request, administrator.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Administrator account created", account));
    }

    @GetMapping("/admin/registration-status")
    public ResponseEntity<ApiResponse<Boolean>> adminRegistrationStatus() {
        return ResponseEntity.ok(ApiResponse.ok(
                adminAccountService.isInitialAdminRegistrationAvailable()));
    }

    @PostMapping("/admin/bootstrap")
    public ResponseEntity<ApiResponse<AdminAccountDto>> registerInitialAdmin(
            @Valid @RequestBody AdminAccountCreateRequest request) {
        AdminAccountDto account = adminAccountService.registerInitialAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Initial administrator account created", account));
    }

    @PostMapping("/admin/forgot-password")
    public ResponseEntity<ApiResponse<Void>> requestAdminPasswordReset(
            @Valid @RequestBody AdminPasswordResetEmailRequest request) {
        adminAccountService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "If an administrator account exists for that email, a password reset link has been sent.",
                null));
    }

    @PostMapping("/admin/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetAdminPassword(
            @Valid @RequestBody AdminPasswordResetRequest request) {
        adminAccountService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Administrator password reset successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> requestCustomerPasswordReset(
            @Valid @RequestBody AdminPasswordResetEmailRequest request) {
        adminAccountService.requestCustomerPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "If a customer account exists for that email, a password reset link has been sent.",
                null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetCustomerPassword(
            @Valid @RequestBody AdminPasswordResetRequest request) {
        adminAccountService.resetCustomerPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully", null));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.ok("Google login successful", response));
    }
}
