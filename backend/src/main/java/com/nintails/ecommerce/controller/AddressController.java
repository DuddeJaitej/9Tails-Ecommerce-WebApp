package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.AddressDto;
import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    // GET /api/addresses
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                addressService.getUserAddresses(user.getUsername())));
    }

    // POST /api/addresses
    @PostMapping
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AddressDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Address added",
                        addressService.addAddress(user.getUsername(), dto)));
    }

    // PUT /api/addresses/{id}
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressDto>> updateAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AddressDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Address updated",
                addressService.updateAddress(user.getUsername(), id, dto)));
    }

    // DELETE /api/addresses/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        addressService.deleteAddress(user.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.ok("Address deleted", null));
    }
}
