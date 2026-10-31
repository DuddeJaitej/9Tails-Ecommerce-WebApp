package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.LoginRequest;
import com.nintails.ecommerce.dto.OwnerRegistrationRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.security.JwtUtils;
import com.nintails.ecommerce.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRoleSeparationTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private Authentication authentication;

    @InjectMocks private AuthService authService;

    @Test
    void customerLoginRejectsAdministratorAccounts() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(User.Role.ADMIN)));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(request("OWNER@example.com")));
        verify(jwtUtils, org.mockito.Mockito.never()).generateToken(any(UserDetails.class));
    }

    @Test
    void administratorLoginRejectsCustomerAccounts() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(User.Role.USER)));

        assertThrows(BadCredentialsException.class,
                () -> authService.loginAdmin(request("customer@example.com")));
        verify(jwtUtils, org.mockito.Mockito.never()).generateToken(any(UserDetails.class));
    }

    @Test
    void administratorLoginAcceptsOnlyAnAdministratorAccount() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(User.Role.ADMIN)));
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("owner@example.com")
                        .password("encoded")
                        .authorities("ROLE_ADMIN")
                        .build());
        when(jwtUtils.generateToken(any(UserDetails.class))).thenReturn("admin-jwt");

        assertEquals("ADMIN", authService.loginAdmin(request("owner@example.com")).getRole());
    }

    @Test
    void operationsLoginAcceptsAdministratorWithoutAnAccountTypeSelector() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(User.Role.ADMIN)));
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("owner@example.com")
                        .password("encoded")
                        .authorities("ROLE_ADMIN")
                        .build());
        when(jwtUtils.generateToken(any(UserDetails.class))).thenReturn("admin-jwt");

        assertEquals("ADMIN", authService.loginOperations(request("owner@example.com")).getRole());
    }

    @Test
    void ownerRegistrationCreatesOnlyAnOwnerAccountAndReturnsSession() {
        OwnerRegistrationRequest request = new OwnerRegistrationRequest();
        request.setFullName("  Business Owner  ");
        request.setEmail(" OWNER@EXAMPLE.COM ");
        request.setPassword("owner-password-123");
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("owner@example.com")
                        .password("encoded")
                        .authorities("ROLE_OWNER")
                        .build());
        when(jwtUtils.generateToken(any(UserDetails.class))).thenReturn("owner-jwt");

        var result = authService.registerOwner(request);

        assertEquals("OWNER", result.getRole());
        assertEquals("owner@example.com", result.getEmail());
        assertEquals("owner-jwt", result.getToken());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                saved -> saved.getRole() == User.Role.OWNER
                        && saved.getFullName().equals("Business Owner")));
    }

    private LoginRequest request(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("test-password");
        return request;
    }

    private User user(User.Role role) {
        return User.builder()
                .id(1L)
                .fullName("Test Account")
                .email(role == User.Role.ADMIN ? "owner@example.com" : "customer@example.com")
                .password("encoded")
                .role(role)
                .build();
    }
}
