package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AuthResponse;
import com.nintails.ecommerce.dto.GoogleLoginRequest;
import com.nintails.ecommerce.dto.GoogleUserInfo;
import com.nintails.ecommerce.dto.LoginRequest;
import com.nintails.ecommerce.dto.OwnerRegistrationRequest;
import com.nintails.ecommerce.dto.RegisterRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.security.JwtUtils;
import com.nintails.ecommerce.security.UserDetailsServiceImpl;
import org.springframework.security.authentication.BadCredentialsException;
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
import java.util.Set;
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
                .photo(user.getProfilePhotoUrl())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse registerOwner(OwnerRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered: " + email);
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.OWNER)
                .build();
        userRepository.save(Objects.requireNonNull(user));

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .photo(user.getProfilePhotoUrl())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        return authenticate(request, Set.of(
                User.Role.USER, User.Role.OWNER, User.Role.DELIVERY_AGENT,
                User.Role.WAREHOUSE_OPERATOR));
    }

    public AuthResponse loginOperations(LoginRequest request) {
        return authenticate(request, Set.of(
                User.Role.OWNER, User.Role.DELIVERY_AGENT,
                User.Role.WAREHOUSE_OPERATOR, User.Role.ADMIN));
    }

    public AuthResponse loginAdmin(LoginRequest request) {
        return authenticate(request, Set.of(User.Role.ADMIN));
    }

    private AuthResponse authenticate(LoginRequest request, Set<User.Role> allowedRoles) {
        String email = request.getEmail().trim().toLowerCase();
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email, request.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!allowedRoles.contains(user.getRole())) {
            throw new BadCredentialsException("Invalid credentials for this sign-in page");
        }

        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .photo(user.getProfilePhotoUrl())
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
            throw new ExternalServiceUnavailableException(
                    "Google sign-in is temporarily unavailable. Please try again.", e);
        }

        if (googleUser == null || !Boolean.TRUE.equals(googleUser.getEmailVerified())
                || googleUser.getEmail() == null || googleUser.getEmail().isBlank()) {
            throw new BadRequestException("Google did not provide a verified email address");
        }

        String email = googleUser.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email)
                .map(user -> user.getRole() == User.Role.ADMIN)
                .orElse(false)) {
            throw new BadCredentialsException("Administrator accounts must use admin sign-in");
        }
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            String fullName = googleUser.getName() == null || googleUser.getName().isBlank()
                    ? email.substring(0, email.indexOf('@'))
                    : googleUser.getName().trim();
            return userRepository.save(Objects.requireNonNull(User.builder()
                    .fullName(fullName)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(User.Role.USER)
                    .profilePhotoUrl(googleUser.getPicture())
                    .build()));
        });
        if (googleUser.getName() != null && !googleUser.getName().isBlank()) {
            user.setFullName(googleUser.getName().trim());
        }
        if (googleUser.getPicture() != null && !googleUser.getPicture().isBlank()) {
            user.setProfilePhotoUrl(googleUser.getPicture());
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return AuthResponse.builder()
                .token(jwtUtils.generateToken(userDetails))
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .photo(user.getProfilePhotoUrl())
                .role(user.getRole().name())
                .build();
    }
}
