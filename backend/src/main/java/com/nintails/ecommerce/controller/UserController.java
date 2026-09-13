package com.nintails.ecommerce.controller;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Map<String, Object> data = Map.of(
                "id",       user.getId(),
                "fullName", user.getFullName(),
                "email",    user.getEmail(),
                "role",     user.getRole().name(),
                "createdAt", user.getCreatedAt()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // GET /api/users  [ADMIN]
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userRepository.findAll().stream()
                .map(u -> Map.of(
                        "id",       u.getId(),
                        "fullName", u.getFullName(),
                        "email",    u.getEmail(),
                        "role",     u.getRole().name()
                )).toList()));
    }
}
