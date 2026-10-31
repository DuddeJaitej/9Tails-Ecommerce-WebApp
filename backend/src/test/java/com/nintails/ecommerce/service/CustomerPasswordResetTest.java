package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AdminPasswordResetEmailRequest;
import com.nintails.ecommerce.dto.AdminPasswordResetRequest;
import com.nintails.ecommerce.entity.AdminPasswordResetToken;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.repository.AdminPasswordResetTokenRepository;
import com.nintails.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPasswordResetTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminPasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AdminAccountService accountService;

    @BeforeEach
    void configureMail() {
        ReflectionTestUtils.setField(accountService, "smtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(accountService, "smtpUsername", "sender@example.com");
        ReflectionTestUtils.setField(accountService, "fromAddress", "sender@example.com");
        ReflectionTestUtils.setField(accountService, "customerResetUrl", "http://localhost:5500/Home/Home.html");
    }

    @Test
    void customerResetRequestSendsSingleUseLinkToCustomerAccount() {
        User customer = User.builder()
                .id(20L)
                .fullName("Customer")
                .email("customer@example.com")
                .password("encoded")
                .role(User.Role.USER)
                .build();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        AdminPasswordResetEmailRequest request = new AdminPasswordResetEmailRequest();
        request.setEmail("CUSTOMER@example.com");
        accountService.requestCustomerPasswordReset(request);

        ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(email.capture());
        assertTrue(email.getValue().getSubject().contains("customer password"));
        assertTrue(email.getValue().getText().contains(
                "http://localhost:5500/Home/Home.html?resetToken="));
        verify(resetTokenRepository).save(any());
    }

    @Test
    void customerResetChangesPasswordAndConsumesToken() throws NoSuchAlgorithmException {
        String rawToken = "customer-reset-token";
        User customer = User.builder()
                .id(20L)
                .fullName("Customer")
                .email("customer@example.com")
                .password("old-password")
                .role(User.Role.USER)
                .build();
        AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                .user(customer)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(resetTokenRepository.findByTokenHashForUpdate(hash(rawToken)))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-secure-password")).thenReturn("encoded-new-password");

        AdminPasswordResetRequest request = new AdminPasswordResetRequest();
        request.setToken(rawToken);
        request.setPassword("new-secure-password");
        accountService.resetCustomerPassword(request);

        verify(userRepository).save(customer);
        verify(resetTokenRepository).deleteByUserId(20L);
        assertTrue(customer.getPassword().equals("encoded-new-password"));
    }

    @Test
    void customerResetDoesNotAcceptAdministratorToken() throws NoSuchAlgorithmException {
        String rawToken = "admin-reset-token";
        User administrator = User.builder()
                .id(30L)
                .fullName("Administrator")
                .email("admin@example.com")
                .password("old-password")
                .role(User.Role.ADMIN)
                .build();
        AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                .user(administrator)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(resetTokenRepository.findByTokenHashForUpdate(hash(rawToken)))
                .thenReturn(Optional.of(resetToken));
        AdminPasswordResetRequest request = new AdminPasswordResetRequest();
        request.setToken(rawToken);
        request.setPassword("new-secure-password");

        assertThrows(BadRequestException.class, () -> accountService.resetCustomerPassword(request));
        verify(userRepository, never()).save(any(User.class));
        verify(resetTokenRepository, never()).deleteByUserId(eq(30L));
    }

    private String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
