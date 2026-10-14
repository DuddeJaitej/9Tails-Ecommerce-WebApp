package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AuthResponse;
import com.nintails.ecommerce.dto.GoogleLoginRequest;
import com.nintails.ecommerce.dto.GoogleUserInfo;
import com.nintails.ecommerce.dto.LoginRequest;
import com.nintails.ecommerce.dto.RegisterRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.security.JwtUtils;
import com.nintails.ecommerce.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .build();

        userRepository.save(Objects.requireNonNull(user));

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        String accessToken = request.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException("Google access token is required");
        }

        GoogleUserInfo googleUser;
        try {
            googleUser = RestClient.create()
                    .get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleUserInfo.class);
        } catch (RestClientResponseException e) {
            throw new BadRequestException("Google sign-in token is invalid or expired");
        } catch (RestClientException e) {
            throw new IllegalStateException("Google authentication service is unavailable", e);
        }

        if (googleUser == null || !Boolean.TRUE.equals(googleUser.getEmailVerified())
                || googleUser.getEmail() == null || googleUser.getEmail().isBlank()) {
            throw new BadRequestException("Google did not provide a verified email address");
        }

        String email = googleUser.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            String fullName = googleUser.getName() == null || googleUser.getName().isBlank()
                    ? email.substring(0, email.indexOf('@'))
                    : googleUser.getName().trim();
            return userRepository.save(Objects.requireNonNull(User.builder()
                    .fullName(fullName)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(User.Role.USER)
                    .build()));
        });

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return AuthResponse.builder()
                .token(jwtUtils.generateToken(userDetails))
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
