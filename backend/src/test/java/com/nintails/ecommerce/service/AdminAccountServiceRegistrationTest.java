package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AdminAccountCreateRequest;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.repository.AdminPasswordResetTokenRepository;
import com.nintails.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceRegistrationTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminPasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AdminAccountService adminAccountService;

    @Test
    void firstAdministratorCanRegisterWhenNoAdminExists() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail("first-admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-admin-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = adminAccountService.registerInitialAdmin(
                request("FIRST-ADMIN@example.com", "strong-admin-password"));

        assertEquals("first-admin@example.com", account.getEmail());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getRole() == User.Role.ADMIN
                        && user.getEmail().equals("first-admin@example.com")
                        && user.getPassword().equals("encoded-password")));
    }

    @Test
    void firstAdministratorRegistrationIsUnavailableAfterAdminExists() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> adminAccountService.registerInitialAdmin(
                request("another-admin@example.com", "strong-admin-password")));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionedAdministratorCanCreateAnotherAdministrator() {
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-admin-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = adminAccountService.registerAdmin(
                request("OWNER@example.com", "strong-admin-password"), "admin@example.com");

        assertEquals("owner@example.com", account.getEmail());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getRole() == User.Role.ADMIN
                        && user.getEmail().equals("owner@example.com")
                        && user.getPassword().equals("encoded-password")));
    }

    @Test
    void registrationStatusIsAvailableOnlyBeforeTheFirstAdminExists() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(0L, 1L);

        org.junit.jupiter.api.Assertions.assertTrue(
                adminAccountService.isInitialAdminRegistrationAvailable());
        org.junit.jupiter.api.Assertions.assertFalse(
                adminAccountService.isInitialAdminRegistrationAvailable());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> adminAccountService.registerAdmin(
                        request("owner@example.com", "strong-admin-password"), "admin@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    private AdminAccountCreateRequest request(String email, String password) {
        AdminAccountCreateRequest request = new AdminAccountCreateRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
